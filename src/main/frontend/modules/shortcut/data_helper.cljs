(ns frontend.modules.shortcut.data-helper
  (:require [frontend.modules.shortcut.config :as config]
            [lambdaisland.glogi :as log]
            [frontend.util :as util]
            [clojure.string :as str]
            [frontend.state :as state]))


(defn binding-map []
  (->> (vals config/default-config)
       (apply merge)
       (map (fn [[k {:keys [binding]}]]
              {k (or (state/get-shortcut k) binding)}))
       (into {})))

(defn- mod-key [shortcut]
  (str/replace shortcut #"(?i)mod"
               (if util/mac? "meta" "ctrl")))

(defn shortcut-binding
  [id]
  (let [shortcut (get (binding-map) id)]
    (cond
      (nil? shortcut)
      (log/error :shortcut/binding-not-found {:id id})

      (false? shortcut)
      (log/debug :shortcut/disabled {:id id})

      :else
      (->>
       (if (string? shortcut)
         [shortcut]
         shortcut)
       (mapv mod-key)))))

;; returns a vector to preserve order
(defn binding-by-category [name]
  (let [dict (->> (vals config/default-config)
                  (apply merge)
                  (map (fn [[k {:keys [i18n]}]]
                         {k {:binding (get (binding-map) k)
                             :i18n    i18n}}))
                  (into {}))]
    (->> (config/category name)
         (mapv (fn [k] [k (k dict)])))))

(defn shortcut-map
  ([handler-id]
   (shortcut-map handler-id nil))
  ([handler-id state]
   (let [raw       (get config/default-config handler-id)
         handler-m (->> raw
                        (map (fn [[k {:keys [fn]}]]
                               {k fn}))
                        (into {}))
         before    (-> raw meta :before)]
     (cond->> handler-m
       state  (reduce-kv (fn [r k handle-fn]
                           (assoc r k (partial handle-fn state)))
                         {})
       before (reduce-kv (fn [r k v]
                           (assoc r k (before v)))
                         {})))))

(defn- decorate-namespace [k]
  (let [n (name k)
        ns (namespace k)]
    (keyword (str "shortcut." ns) n)))
(defn shortcut-dict
  "All docs for EN are generated from :desc field of shortcut default-config map.
  For all other languages, need manual translation in dict file.
  Eg: editor/insert-link would be shortcut.editor/insert-link in dict file"
  []
  {:en
   (->> (vals config/default-config)
        (apply merge)
        (map (fn [[k {:keys [desc]}]]
               {(decorate-namespace k) desc}))
        (into {}))})
