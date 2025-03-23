(defproject codevigil "0.1.0-SNAPSHOT"
  :description "CodeVigil - GitHub repository pull request analysis tool"
  :url "http://example.com/codevigil"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [dev.nubank/clj-github "0.7.1"]
                 [org.clojure/core.async "1.6.673"]
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [metosin/reitit "0.6.0"]
                 [metosin/muuntaja "0.6.8"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [org.postgresql/postgresql "42.6.0"]
                 [environ "1.2.0"]
                 [cheshire "5.11.0"]
                 [clojure.java-time "1.4.3"]
                 [org.clojure/java.jdbc "0.7.12"]]
  :main ^:skip-aot codevigil.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.clojure/tools.namespace "1.4.4"]]
                   :plugins [[lein-environ "1.2.0"]]}}
  :repl-options {:init-ns codevigil.core})
