(ns scraper
  (:require
   [clojure.core.async :as async]
   [clojure.data.json :refer [read-str]]
   [cuerdas.core :as hstr]
  ;;  [clojure.tools.logging :as log]
   [clojure.string :refer [escape trim trim-newline]]
   [etaoin.api :as d]))

(defrecord Scraper [driver in-ch out-ch])

(def ld-json-query {:tag :script :type "application/ld+json"})
(def id-json-query {:id "ProductJson-product-template"})

(defn- query
  [driver q]
  (let [ids (d/query-all driver q)]
    #_(log/info (str "Number ld+json's: " (count ids)))
    (loop [xs ids
           result []]
      (if xs
        (let [x (first xs)
              h (d/get-element-inner-html-el driver x)]
          (recur (next xs) (conj result h)))
        result))))

(defn ld-json->product
  [driver url]
  (let [_ (d/go driver url)
        _ (d/wait driver 5)
        _ (d/wait-exists driver ld-json-query {:timeout 10})
        inner (query driver ld-json-query)]
    #_(log/info (str "Inner: " inner))
    (filter #(= (.toLowerCase (:type %)) "product")
            (map #(-> %
                      (escape {\@ ""})
                      (read-str :key-fn keyword)) inner))))

(defn id-json->product
  [driver url]
  (let [_ (d/go driver url)
        _ (d/wait driver 5)
        _ (d/wait-exists driver id-json-query {:timeout 10})
        inner (query driver id-json-query)]
    #_(log/info (str "Inner: " inner))
    (map #(-> %
              (escape {\@ ""})
              (read-str :key-fn keyword)) inner)))

(defn init-scraper [base-url f]
  (let [driver  (d/firefox {:headless false})
        in-ch   (async/chan)
        out-ch  (async/chan)
        _       (d/go driver base-url)
        scraper (map->Scraper {:driver driver
                               :in    in-ch
                               :out   out-ch})]
    (async/go-loop []
      (when-let [url (async/<! in-ch)]
        #_(log/info (str "Received URL to scrape " url))
        (when (async/>!! out-ch (f driver url))
          (recur))))

    scraper))

(defn close
  [^Scraper scraper]
  (async/close! (:in scraper))
  (async/close! (:out scraper))
  (d/quit (:driver scraper)))

(def driver (d/firefox {:headless false}))
(d/go driver "https://milliondollarbaby.com/products/darlington-assembled-nightstand")

(def inner (trim (trim-newline (hstr/strip-tags (d/get-element-inner-html driver {:class "product-description tab-text"})))))
(re-find (re-matcher #"Assembled Dimensions: (.*)" inner))
(re-find (re-matcher #"Assembled Weight: (.*)" inner))
(d/quit driver)