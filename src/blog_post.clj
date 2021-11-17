(ns blog-post)

(require '[clojure.xml :as xml])

(defn parse-xml [url]
  (try (xml/parse url)
       (catch Exception e
         (println
          (str "Caught Exception parsing xml: " (.getMessage e))))))

;; (parse-xml "https://bellroy.com/sitemap.xml")

(defn get-content
  [content]
  (into {} (map (fn [c] [(:tag c) (first (:content c))]) (:content content))))

(defn find-products
  "Crawls the url's sitemap and for each product, will call f with the site and the product"
  [site url f]
  (let [p (parse-xml url)
        tag (:tag p)]
    (println (str "Parsing URL: " url " for site " site))
    (if p
      (cond
        (= tag :urlset) (doseq [mm (map get-content (:content p))] (f site mm))
        (= tag :sitemapindex) (let [ccs (map get-content (:content p))
                                    pps (vec (map :loc ccs))]
                                (doseq [mm pps] (find-products site mm f)))
        :else (println "none"))
      (println (str "Unable to process url for " site)))
    (println "Done finding products")))

;; (find-products "bellroy" "https://bellroy.com/sitemap.xml" println)

(require '[taoensso.carmine :as car])

;; Redis Setup
(def redis-conn {:pool {} :spec {:uri "redis://localhost:6379/"}})
(defmacro wcar* [& body] `(car/wcar redis-conn ~@body))

(defn clear-site
  [^String site]
  (wcar* (car/del site)))

(defn get-count
  [^String site]
  (wcar* (car/llen site)))

(require '[clojure.string :refer [includes?]])

(defn product->cache [site mmap]
  (when-let [url (:loc mmap)]
    (when (includes? url "/products")
      (wcar* (car/rpush site mmap)))))

(defn cache->product [site] (wcar* (car/lpop site)))

(clear-site "bellroy") ;; Clear cache first
(find-products "bellroy" "https://bellroy.com/sitemap.xml" product->cache)
(get-count "bellroy")
;; (cache->product "bellroy")
;; (cache->product "bellroy")

(require '[etaoin.api :as d])

(def driver (d/firefox {:headless false}))
(d/go driver (:loc (cache->product "bellroy")))

;; Query <script type="application/ld+json"> tags:
(def ld-json-query {:tag :script :type "application/ld+json"})
(def ld-json-ids (d/query-all driver ld-json-query))
;; => ["c84039d5-8813-4c40-8d05-6a0e49938883" "15b1c505-a420-ed4f-96a9-e8758067535f"]

(require '[clojure.data.json :refer [read-str]])

;; Only two ld+json script tags-- get the content:
(read-str (d/get-element-inner-html-el driver (first ld-json-ids)))
;; => {"@context" "https://schema.org", "@type" "BreadcrumbList" ...}
(read-str (d/get-element-inner-html-el driver (last ld-json-ids)))
;; => {"@context" "https://schema.org/", "@type" "product" ...}

(require '[clojure.string :refer [escape]])

;; Remove '@' symbols and Build map
(def product-json
  (read-str
   (escape
    (d/get-element-inner-html-el driver (last ld-json-ids))
    {\@ ""})
   :key-fn keyword))

(println product-json)
;; => {:context "https://schema.org/", :type "product", :brand "Bellroy" ...}

(d/quit driver)

;; Mongo
(require '[somnium.congomongo :as m])

;; Products database
(def mongo-conn
  (m/make-connection
   "products"
   :instances [{:host "127.0.0.1" :port 27017}]))

;; Set global connection
(m/set-connection! mongo-conn)

;; Add product offers to "bellroy" collection
(m/mass-insert! "bellroy" (:offers product-json))
