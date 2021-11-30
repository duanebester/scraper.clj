# Web scraping w/ Clojure

As seen in the blog post: [Web Scraping with Clojure](https://medium.com/geekculture/scraping-web-product-data-with-clojure-6594a86c2f00)

### Docker

Stand up Redis and Mongo

```bash
docker compose up -d
```

Mostly everything is in [core.clj](./src/core.clj). The code from the blog post is in [blog_post.clj](./src/blog_post.clj).

Uses async channels to send urls to the Scraper record.
