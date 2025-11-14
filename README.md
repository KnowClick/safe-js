# Safe JS

Safely produce javascript strings including user inputs in your clojure app. Mainly intended for use with [Datastar](https://data-star.dev/).

## Why

In Datastar apps, we often need to create snippets of javascript. The existing options for that are:
- String concatenation/`format`
- [datastar-expressions](https://github.com/outskirtslabs/datastar-expressions/tree/main)

When user inputs could plausibly be included in these javascript snippets (e.g. `:data-init (str "alert('Hello from " username "')")`), we run the risk of opening ourselves up to injection attacks (like in the example we just saw).

Datastar-expressions is a noble attempt at implementing these snippets with Clojure's syntax, but my measurements have shown that there's a lot of overhead from translating those snippets to strings.

This library requires us to write javascript in strings, which we all agree is repulsive, but do so safely and with little overhead.

## API

### com.knowclick.safe-js/str

Accepts one or more forms, typically strings.

Provides 4 interpolation forms in strings:
- #{} to insert escaped forms
- #() to insert escaped invocations
- #!{} to insert unescaped forms
- #!() to insert unescaped forms invocations

Returns an escaped string.

Use like `clojure.core/str` if you don't like interpolation:

```clojure
(def quality "bad")
(com.knowclick.safe-js/str "console.log(" quality ")")
;;=> "console.log(\"bad\")"
```

Pass maps/vectors/etc if it pleases you:

```clojure
(def user {:name "bart" :action "alert('xss')"})
(js/str user)
;; => "{\"name\":\"bart\",\"action\":\"alert('xss')\"}"

;; And if you want Bart to really get into trouble:
(def user {:name "bart" :action (js/! "alert('xss')")})
;; => "{\"name\":\"bart\",\"action\":alert('xss')}"
```

### com.knowclick.safe-js/!

Given a string (presumably of javascript code), returns an object that will allow that string to pass through escaping functions and macros un-escaped.

### com.knowclick.safe-js/escape

Sanitize some data. Data wrapped with `!` are not escaped.

### com.knowclick.safe-js/str!

Like `str` but wraps the result in `!`.
