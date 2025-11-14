(ns com.knowclick.safe-js-test
  (:require
   [com.knowclick.safe-js :as js]
   [clojure.test :as t :refer [deftest testing is]]))

(deftest safe-js
  (testing "Works like clojure.core/str in basic cases"
    (is (= (js/str "console.log('hello')")
           (str "console.log('hello')"))))

  (testing "Handles nil"
    (is (= (js/str "foo" nil "bar")
           (str "foobar")))

    (is (= (js/str nil) (str ""))))

  (testing "String interpolation"
    (let [var "hello"]
      (is (= (js/str "console.log(#{ var })")
             (str "console.log(\"" var "\")")))

      (is (= (js/str "console.log(#!{ var })")
             (str "console.log(" var ")")))

      (is (= (js/str "console.log(#(str var \" world\"))")
             (str "console.log(\"" var " world" "\")")))

      (is (= (js/str "console.log(#!(str var \" world\"))")
             (str "console.log(" var " world" ")")))))

  (testing "Escape/unescape"
    (let [var "alert('xss')"]
      (is (= (js/str "console.log(#{ \"alert('xss')\" })")
             (js/str "console.log(#{ var })")
             (str "console.log(\"" var "\")")))

      (is (= (js/str "console.log(#!{ \"alert('xss')\" })")
             (js/str "console.log(#!{ var })")
             (str "console.log(" var ")")))

      (is (= (js/str "console.log(" (js/! var) ")")
             (str "console.log(" var ")")))))

  (testing "Data structures"
    (is (= (js/str {:danger (js/! "alert('xss')")})
           "{\"danger\":alert('xss')}"))

    (is (= (js/str {:safety "alert('xss')"})
           "{\"safety\":\"alert('xss')\"}"))

    (testing "Nested structures"
      (is (= (js/str [{:a [{"b" "alert('xss')"}]}])
             "[{\"a\":[{\"b\":\"alert('xss')\"}]}]"))

      (is (= (js/str [{:a [{"b" (js/! "alert('xss')")}]}])
             "[{\"a\":[{\"b\":alert('xss')}]}]")))

    ))

(comment
  (t/run-tests))
