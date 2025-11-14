(ns com.knowclick.safe-js
  "Utilities for safely producing js code.
  Public API is:
  - `escape`
  - `str`
  - `!`
  - `str!`
  - `enable-pretty-print`"
  (:refer-clojure :exclude [str])
  (:require
   [clojure.core :as c]
   [clojure.data.json :as cdj]
   [clojure.string :as string]
   [charred.api :as charred]
   [charred.coerce :as coerce])
  (:import [java.util List Map Map$Entry]
           [java.util.function BiConsumer]
           [charred JSONWriter]
           [clojure.lang MapEntry]))

(deftype Unescaped [s]
  Object
  (toString [_this] (c/str s)))

(defn !
  "Given a string (presumably of javascript code), returns an object that will allow that string to pass through `escape` un-escaped."
  [s] (->Unescaped s))

(defn- default-write-fn [x _out _options]
  (throw (ex-info "Don't know how to convert object to JSON."
                  {:object x
                   :class (class x)})))

(defn- default-object-writer [^JSONWriter w value]
  (let [value (when-not (nil? value) (charred/->json-data value))]
    (cond
      (or (sequential? value)
          (instance? List value)
          (.isArray (.getClass ^Object value)))
      (.writeArray w (coerce/->iterator value))
      (instance? Map value)
      (.writeMap w (coerce/map-iter (fn [^Map$Entry e]
                                      (MapEntry. (charred/->json-data (.getKey e))
                                                 (.getValue e)))
                                    (.entrySet ^Map value)))
      :else
      (.writeObject w value))))

(defn- charred-object-writer [writer obj]
  (if (instance? Unescaped obj)
    (.write (.-w writer) (.toString obj))
    (default-object-writer writer obj)))

(def ^:private charred-writer
  (charred/write-json-fn {:obj-fn
                          (reify BiConsumer
                            (accept [_this writer value]
                              (charred-object-writer writer value)))}))

(defn- charred-escape [x]
  (with-open [output (java.io.StringWriter.)]
    (charred-writer output x)
    (.toString output)))

(def ^:dynamic *pretty-print* false)

(defn enable-pretty-print
  "Turn on JSON pretty printing. Useful in dev."
  []
  (alter-var-root #'*pretty-print* (constantly true)))

(comment
  (enable-pretty-print)
  )

(defn- cdj-escape [x]
  (cdj/write-str x
                 {:indent *pretty-print*
                  :escape-slash false
                  :default-write-fn (fn [obj stream options]
                                      (if (instance? Unescaped obj)
                                        (.write stream (.toString obj))
                                        (default-write-fn obj stream options)))}))

(comment
  ;; To my surprise, I'm seeing better perf from clojure.data.json than from charred.
  ;; Why?
  (time
   (dotimes [_n 100000]
     (charred-escape {:foo "bar" :baz (! "5 + 6")})))

  (time
   (dotimes [_n 100000]
     (cdj-escape {:foo "bar" :baz (! "5 + 6")}))))

(defn escape
  "Sanitize some data. Data wrapped with `!` are not escaped."
  [x]
  (cdj-escape x))

(def ^:private syntaxes
  {"#!{" {:transform nil
          :atomic true
          :doc "directly insert a var/form"}
   "#!(" {:transform nil
          :doc "directly insert an evaluated form"}
   "#{"  {:transform `escape
          :atomic true
          :doc "insert an escaped var/form"}
   "#("  {:transform `escape
          :doc "insert an evaluated and escaped form"}})

(defn- silent-read
  "Attempts to clojure.core/read a single form from the provided String, returning
  a vector containing the read form and a String containing the unread remainder
  of the provided String. Returns nil if no valid form can be read from the
  head of the String."
  [s]
  (try
    (let [r (-> s java.io.StringReader. clojure.lang.LineNumberingPushbackReader.)]
      [(read r) (string/triml (slurp r))])
    (catch Exception _e)))

(defn- interpolate
  "Yields a seq of Strings and read forms."
  ([s syntax]
   (let [{:keys [atomic transform]} (get syntaxes syntax)]
     (if-let [[form rest] (silent-read
                           (subs s (if atomic
                                     (count syntax)
                                     ;; include the open paren for non-atomic:
                                     (dec (count syntax)))))]
       (cons (if transform
               (list transform form)
               form)
             (interpolate (if atomic
                            (subs rest 1) ;; get rid of the dangling '}'.
                            rest)))
       (cons (subs s 0 (count syntax))
             (interpolate (subs s (count syntax)))))))
  ([^String s]
   (if-let [start (->> (keys syntaxes)
                       (map (fn [^String syntax]
                              {:index (string/index-of s syntax)
                               :syntax syntax}))
                       (remove #(nil? (:index %)))
                       (sort-by :index)
                       first)]
     (let [{:keys [index syntax]} start]
       (into [(subs s 0 index)]
             (let [reststr (subs s index)]
               (interpolate reststr syntax))))
     [s])))

(defn- join-strings [forms]
  (loop [forms forms
         out   []
         last-str nil]
    (if-let [x (first forms)]
      (recur (rest forms)
             (if (string? x)
               out
               (if last-str
                 (conj out last-str x)
                 (conj out x)))
             (if (string? x)
               (c/str last-str x)
               nil))
      (if last-str
        (conj out last-str)
        out))))

(defmacro str
  "Accepts one or more forms, typically strings.

  Provides 4 interpolation forms in strings:
  #{} to insert escaped forms
  #() to insert escaped invocations
  #!{} to insert unescaped forms
  #!() to insert unescaped forms invocations

  (let [signal-name \"foo\"
        user-input  \";;; alert('xss')\"]
    (str \"$#!{ signal-name } = #(subs user-input 2);\"))
  ;; => \"$foo = \\\"; alert('xss')\\\";\"

  With thanks to Chas Emerick (https://cemerick.com/blog/2009/12/04/string-interpolation-in-clojure.html)"
  [& forms]
  (let [forms (join-strings forms)
        forms (->> forms
                   (mapcat (fn [x]
                             (if (string? x)
                               (interpolate x)
                               [(list `escape x)])))
                   (remove (fn [s] (= s ""))))]
    (if (= (count forms) 1)
      (first forms)
      `(c/str ~@forms))))

(defmacro str!
  "Like `str` but wraps the result in `!`."
  [& forms]
  `(! (str ~@forms)))
