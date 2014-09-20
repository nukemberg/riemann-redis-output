# riemann-flapjack-output

A riemann [Flapjack](http://flapjack.io/) plugin.

## Usage

In your riemann.config

```clojure

(load-plugins) ; will load plugins from the classpath

(flapjack/output)

```

Or with options:

```clojure
(load-plugins)
(flapjack/output {:conn-spec {:host "redis" :port 6379 :db 13} :buff-size 1000 :transcoder flapjack/transcode-event})
```

Options:
- `:conn-spec` - [Carmine](https://github.com/ptaoussanis/carmine) redis connection spec. See Carmine docs for more info
- `:buff-size` - Size of the internal event queue. Default is 1000. Events will be flushed asyncronously from queue to redis.
- `:transcoder` - A function to convert Riemann event map to a Flapjack compatible structure. Defaults to `flapjack/transcode-event`, see the source for more info.

### Flapjack event fields:

By default, the transcoding function will rename keys as follows:

:service -> :check, :host -> :entity, :metric -> :perfdata, :description -> :summary

Flapjack is aware of more fields which may be of use: :details, :type, :state. If they are not present they will be assigned default values.
See Flapjack docs for more info on these fields.


## Installing

You will need to build this module for now and push it on riemann's classpath, for this
you will need a working JDK, JRE and [leiningen](http://leiningen.org).

First build the project:

```
lein uberjar
```

The resulting artifact will be in `target/riemann-flapjack-output-standalone-0.0.1.jar`.
You will need to push that jar on the machine(s) where riemann runs, for instance, in
`/usr/lib/riemann/riemann-flapjack-output.jar`.

If you have installed riemann from a stock package you will only need to tweak
`/etc/default/riemann` and change
the line `EXTRA_CLASSPATH` to read:

```
EXTRA_CLASSPATH=/usr/lib/riemann/riemann-flapjack-output.jar
```

You can then use exposed functions, provided you have loaded the plugin in your configuration.

## License

Copyright © 2014 Avishai Ish-Shalom

Distributed under the Apache V2 License
