(ns sitemap
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :refer [includes?]]
   [clojure.xml :as xml]))

;; XML fetching and parsing
(defn- get-content
  [content]
  (into {} (map (fn [c] [(:tag c) (first (:content c))]) (:content content))))

(defn- parse-xml [url]
  (try (xml/parse url)
       (catch Exception e
         (log/error
          (str "Caught Exception parsing xml: " (.getMessage e))))))

;; (def parse (memoize parse-xml))

(defn find-products
  "Crawls the url's sitemap and for each product, will call f with the site and the product"
  [site url f]
  (let [p (parse-xml url)
        tag (:tag p)]
    (log/info (str "Parsing URL: " url " for site " site))
    (if p
      (cond
        (= tag :urlset) (doseq [mm (map get-content (:content p))] (f site mm))
        (= tag :sitemapindex) (let [ccs (map get-content (:content p))
                                    ps  (filter #(includes? (:loc %) "product") ccs)
                                    pps (take 3 (vec (map :loc ps)))]
                                (doseq [mm pps] (find-products site mm f)))
        :else (println "none"))
      (log/error (str "Unable to process url for " site)))
    (log/info "Done finding products")))
