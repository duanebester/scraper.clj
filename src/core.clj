(ns core
  (:require
   [cache :refer [clear-site get-count ->cache cache->]]
   [clojure.core.async :as async]
   [clojure.tools.logging :as log]
   [scraper :refer [init-scraper close id-json->product]]
   [sitemap :refer [find-products]]
   [somnium.congomongo :as m]))

(def mongo
  (m/make-connection "products"
                     :instances [{:host "127.0.0.1" :port 27017}]))

(m/set-connection! mongo)

(def site "bellroy")
(def base-url "https://bellroy.com")
;; (def robots-url (str base-url "/robots.txt"))
(def sitemap-url (str base-url "/sitemap.xml"))

(defn flatten-products
  [products-json url]
  (let [name (:title products-json)
        desc (:description products-json)
        brand (:brand products-json)
        price (:price products-json)
        category (:type products-json)
        offers (:variants products-json)
        mmap {:url url
              :price price
              :brand brand
              :description desc
              :category category
              :name name}]
    (map #(conj mmap %) offers)))

(clear-site site)
(find-products site sitemap-url ->cache)
(get-count site)

(def scraper (init-scraper base-url id-json->product))

(async/go-loop []
  (when-let [mm (async/<! (:out scraper))]
    (when (seq mm)
      (dorun
       (m/mass-insert! site
                       (flatten (map flatten-products mm base-url)))))
    (log/info (str "Inserted coll, fetching more..."))
    (when (async/>!! (:in scraper) (:loc (cache-> site)))
      (recur))))

(async/>!! (:in scraper) (:loc (cache-> site)))

(close scraper)
(m/close-connection mongo)
(cache-> site)