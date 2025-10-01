#!/usr/bin/env bb

(ns gnucash-backup
  "
    Backs up a GnuCash PostgreSQL database.

    USAGE:
      gnucash_backup.bb [OPTIONS]

    OPTIONS:
      -d, --database    The PostgreSQL GnuCash database. Defaults to gnucash_db.
      -h, --help        Display the help message.
      -H, --host        The PostgreSQL host. Defaults to 127.0.0.1.
      -k, --keep        The number of backups to keep. Defaults to 5.
      -o, --output-dir  The directory to save the backup file in. REQUIRED.
      -p, --port        The PostgreSQL port. Defaults to 5432.
      -u, --user        The PostgreSQL database user. Defaults to gnucash_user.
      -v, --version     Display the version of the script.

    PREREQUISITES:
      - Runs on Linux (tested on Linux Mint)
      - Babashka is installed and on the PATH
      - pg_dump is installed
      - A .pgpass file exists in the user's home directory, is readable, and contains a line like: 127.0.0.1:5432:*:gnucash_user:my-secret-password

    AUTHOR:
      jittery-name-ninja@duck.com
  "

  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as proc])
  (:import (java.io RandomAccessFile)
           (java.time LocalDateTime ZoneId)
           (java.time.format DateTimeFormatter)))

(def ^:const cli-options
  [["-d" "--database" "The PostgreSQL GnuCash database"
    :default "gnucash_db"]
   ["-h" "--help" "Show help"]
   ["-H" "--host HOST" "The PostgreSQL host"
    :default "127.0.0.1"]
   ["-k" "--keep COUNT" "The number of backups to keep"
    :parse-fn (fn [s] (try (Long/parseLong s) (catch Exception _ nil)))
    :validate [#(and (number? %) (< 0 %)) "Must be an integer greater than 0"]
    :default 5]
   ["-o" "--output-dir DIR" "The directory to save the backup file in"
    :required "The directory to save the backup file in"]
   ["-p" "--port PORT" "The PostgreSQL port"
    :parse-fn (fn [s] (try (Long/parseLong s) (catch Exception _ nil)))
    :validate [#(and (number? %) (< 0 %)) "Must be an integer greater than 0"]
    :default 5432]
   ["-u" "--user USER" "The PostgreSQL GnuCash database user"
    :default "gnucash_user"]
   ["-v" "--version" "Show version."]])

(def ^:const version "0.0.1")
(def ^:const script-name "gnucash_backup.bb")
(def ^:const lock-file (str (fs/path "/tmp/" (str script-name ".lock"))))

(def ^:const exit-codes
  {:success 0
   :fail    1})

(defonce lock-state (atom nil))

;;; ---------------------------------------------------------------------------
;;; Supporting functions
;;; ---------------------------------------------------------------------------

(defn uid
  "Gets the user id of the current process."
  []
  (try
    (Integer/parseInt (str/trim (:out (proc/sh "id" "-u"))))
    (catch Exception _ -1)))

(defn now-formatted
  "Gets the current date/time, formatted."
  [fmt]
  (.format (LocalDateTime/now (ZoneId/systemDefault))
           (DateTimeFormatter/ofPattern fmt)))

(defn now-ts [] (now-formatted "yyyy-MM-dd HH:mm:ss"))

(defn now-tag [] (now-formatted "yyyyMMdd-HHmmss"))

(defn exit-fail []
  (System/exit (:fail exit-codes)))

(defn exit-success []
  (System/exit (:success exit-codes)))

;;; ---------------------------------------------------------------------------
;;; Set up logging
;;; ---------------------------------------------------------------------------

(defn stderrln
  "Prints to standard error."
  [msg]
  (binding [*out* *err*] (println msg)))

(defn try-spit
  "Attempt to write string s to file f."
  [f s]
  (try (spit f s :append true)
       (catch Exception _ (stderrln (str "Failed to write to " f)))))

;; Put log file in /var/log/ if root, otherwise put it in /tmp/
(def log-file
  (let [filename (str script-name ".log")]
    (if (= (uid) 0)
      (str (fs/path "/var/log/" filename))
      (str (fs/path "/tmp/" filename)))))

(defn log-info
  "Logs INFO messages to log file and stdout."
  [msg]
  (let [ts (now-ts)]
    (println msg)
    (try-spit log-file (str ts " [INFO] " msg "\n"))))

(defn log-error
  "Logs ERROR messages to log file and stderr."
  [msg]
  (let [ts (now-ts)]
    (stderrln msg)
    (try-spit log-file (str ts " [ERROR] " msg "\n"))))

;;; ---------------------------------------------------------------------------
;;; Process command-line arguments
;;; ---------------------------------------------------------------------------

(def parsed-args (cli/parse-opts *command-line-args* cli-options))

;; Check for command-line errors
(let [{:keys [errors]} parsed-args]
  (when errors
    (doseq [e errors] (log-error e))
    (exit-fail)))

(def options (:options parsed-args))

;;; ---------------------------------------------------------------------------
;;; Display help message
;;; ---------------------------------------------------------------------------

(defn help-message
  "Returns the doc string for this namespace, which is the help message."
  []
  (:doc (meta (the-ns 'gnucash-backup))))

(when (:help options)
  (println (help-message))
  (exit-success))

;;; ---------------------------------------------------------------------------
;;; Display version
;;; ---------------------------------------------------------------------------

(when (:version options)
  (println version)
  (exit-success))

;;; ---------------------------------------------------------------------------
;;; Make sure the script isn't already being run
;;; ---------------------------------------------------------------------------

(defn obtain-lock!
  "Try to obtain an exclusive lock on lock-file.
   Returns true on success, nil on failure."
  []
  (try
    (let [raf (RandomAccessFile. lock-file "rw")
          chan (.getChannel raf)
          lock (.tryLock chan)]
      (if (nil? lock)
        (do
          (.close chan)
          (.close raf)
          nil)
        (do
          (reset! lock-state {:raf raf :chan chan :lock lock})
          true)))
    (catch Exception _ nil)))

(.addShutdownHook (Runtime/getRuntime)
                  (Thread. (fn []
                             (when-let [{:keys [raf chan lock]} @lock-state]
                               (try (.release lock) (catch Exception _))
                               (try (.close chan) (catch Exception _))
                               (try (.close raf) (catch Exception _))
                               (try (io/delete-file lock-file true) (catch Exception _))))))

(when-not (obtain-lock!)
  (log-error "Another instance is already running. Exiting.")
  (exit-fail))

;;; ----------------------------------------------------------------------------
;;; Preflight checks
;;; ----------------------------------------------------------------------------

(defn program-exists?
  "Checks if a program exists."
  [prog]
  (some? (fs/which prog)))

(when-not (program-exists? "pg_dump")
  (log-error "pg_dump not found")
  (exit-fail))

(let [dir (:output-dir options)]
  (when-not (and (fs/exists? dir) (fs/writable? dir))
    (log-error (str "Cannot write to directory " dir))
    (exit-fail)))

(let [pgpass (fs/expand-home "~/.pgpass")]
  (when-not (and (fs/exists? pgpass) (fs/readable? pgpass))
    (log-error ".pgpass could not be read")
    (exit-fail)))

;;; ----------------------------------------------------------------------------
;;; Log the startup
;;; ----------------------------------------------------------------------------

(log-info (str "Running " script-name " ..."))
(log-info (str "Version " version))
(log-info (str "Logging to " log-file))
(log-info (str "Lock file " lock-file))
(log-info (str "Database URL postgresql://" (:user options) ":********@"  (:host options) ":" (:port options) "/" (:database options)))

;;; ----------------------------------------------------------------------------
;;; Back up GnuCash
;;; ----------------------------------------------------------------------------

(defn backup-gnucash
  "Dumps a PostgreSQL database using pg_dump."
  []
  (let [host (:host options)
        port (:port options)
        user (:user options)
        database (:database options)
        dumpfile (str "gnucash_" (now-tag) ".dump")
        file (str (fs/path (:output-dir options) dumpfile))
        {:keys [err exit]} (proc/shell
                                 {:out :string :err :string :continue true}
                                 "pg_dump" "-h" host "-p" port "-U" user "-d" database "-f" file "-Fc")]
    (log-info (str "Backing up database to " file))
    (when-not (zero? exit)
      (log-error (str "Unable to back up the GnuCash database: " err))
      (exit-fail))))

(backup-gnucash)

;;; ----------------------------------------------------------------------------
;;; Prune the backups
;;; ----------------------------------------------------------------------------

(defn list-backups [backup-dir]
  (when (fs/exists? backup-dir)
    (->> (fs/list-dir backup-dir)
         (filter fs/regular-file?)
         (map str)
         (filter #(re-find #"^gnucash_\d{8}-\d{6}\.dump$" (fs/file-name %)))
         (sort))))

(defn prune-old-backups!
  "Removes old backup files."
  [backup-dir]
  (let [retention-count (:keep options)
        backups (vec (list-backups backup-dir))]
    (when (> (count backups) retention-count)
      (doseq [old (take (- (count backups) retention-count) backups)]
        (try (fs/delete-if-exists old)
             (catch Exception e
               (log-error (str "Cannot delete backup. " (.getMessage e)))))))))

(log-info (str "Deleting old backups (keeping " (:keep options) ")."))
(prune-old-backups! (:output-dir options))

(log-info "Done")
(exit-success)