! function(t, e) {
    "object" == typeof exports && "undefined" != typeof module ? module.exports = e() : "function" == typeof define && define.amd ? define(e) : (t = "undefined" != typeof globalThis ? globalThis : t || self)
        .Viewer = e()
}(this, function() {
    "use strict";

    function e(e, t) {
        var i, n = Object.keys(e);
        return Object.getOwnPropertySymbols && (i = Object.getOwnPropertySymbols(e), t && (i = i.filter(function(t) {
            return Object.getOwnPropertyDescriptor(e, t)
                .enumerable
        })), n.push.apply(n, i)), n
    }

    function l(n) {
        for (var t = 1; t < arguments.length; t++) {
            var o = null != arguments[t] ? arguments[t] : {};
            t % 2 ? e(Object(o), !0)
                .forEach(function(t) {
                    var e, i;
                    e = n, t = o[i = t], i in e ? Object.defineProperty(e, i, {
                        value: t,
                        enumerable: !0,
                        configurable: !0,
                        writable: !0
                    }) : e[i] = t
                }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(n, Object.getOwnPropertyDescriptors(o)) : e(Object(o))
                .forEach(function(t) {
                    Object.defineProperty(n, t, Object.getOwnPropertyDescriptor(o, t))
                })
        }
        return n
    }

    function i(t) {
        return (i = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function(t) {
            return typeof t
        } : function(t) {
            return t && "function" == typeof Symbol && t.constructor === Symbol && t !== Symbol.prototype ? "symbol" : typeof t
        })(t)
    }

    function o(t, e) {
        for (var i = 0; i < e.length; i++) {
            var n = e[i];
            n.enumerable = n.enumerable || !1, n.configurable = !0, "value" in n && (n.writable = !0), Object.defineProperty(t, n.key, n)
        }
    }
    var s = {
            backdrop: !0,
            button: !0,
            navbar: 0,
            title: !0,
            toolbar: 0,
            className: "",
            container: "body",
            filter: null,
            fullscreen: !0,
            inheritedAttributes: ["crossOrigin", "decoding", "isMap", "loading", "referrerPolicy", "sizes", "srcset", "useMap"],
            initialViewIndex: 0,
            inline: !1,
            interval: 5e3,
            keyboard: !0,
            focus: !0,
            loading: !0,
            loop: !0,
            minWidth: 200,
            minHeight: 100,
            movable: !0,
            rotatable: !0,
            scalable: !0,
            zoomable: !0,
            zoomOnTouch: !0,
            zoomOnWheel: !0,
            slideOnTouch: !0,
            toggleOnDblclick: !0,
            tooltip: !0,
            transition: !0,
            zIndex: 2015,
            zIndexInline: 0,
            zoomRatio: .1,
            minZoomRatio: .01,
            maxZoomRatio: 100,
            url: "originalsrc",
            ready: null,
            show: null,
            shown: null,
            hide: null,
            hidden: null,
            view: null,
            viewed: null,
            move: null,
            moved: null,
            rotate: null,
            rotated: null,
            scale: null,
            scaled: null,
            zoom: null,
            zoomed: null,
            play: null,
            stop: null
        },
        t = "undefined" != typeof window && void 0 !== window.document,
        n = t ? window : {},
        a = !(!t || !n.document.documentElement) && "ontouchstart" in n.document.documentElement,
        r = t && "PointerEvent" in n,
        g = "viewer",
        c = "move",
        u = "switch",
        d = "zoom",
        f = "".concat(g, "-active"),
        v = "".concat(g, "-close"),
        p = "".concat(g, "-fade"),
        b = "".concat(g, "-fixed"),
        w = "".concat(g, "-fullscreen"),
        h = "".concat(g, "-fullscreen-exit"),
        y = "".concat(g, "-hide"),
        m = "".concat(g, "-hide-md-down"),
        x = "".concat(g, "-hide-sm-down"),
        k = "".concat(g, "-hide-xs-down"),
        z = "".concat(g, "-in"),
        D = "".concat(g, "-invisible"),
        T = "".concat(g, "-loading"),
        E = "".concat(g, "-move"),
        I = "".concat(g, "-open"),
        A = "".concat(g, "-show"),
        S = "".concat(g, "-transition"),
        O = "click",
        C = "dblclick",
        L = "dragstart",
        R = "focusin",
        F = "keydown",
        N = "load",
        Y = "error",
        X = r ? "pointerdown" : a ? "touchstart" : "mousedown",
        M = r ? "pointermove" : a ? "touchmove" : "mousemove",
        q = r ? "pointerup pointercancel" : a ? "touchend touchcancel" : "mouseup",
        P = "resize",
        W = "transitionend",
        j = "wheel",
        H = "ready",
        B = "show",
        V = "viewed",
        U = "rotated",
        K = "".concat(g, "Action"),
        Z = /\s\s*/,
        $ = ["zoom-in", "zoom-out", "one-to-one", "reset", "prev", "play", "next", "rotate-left", "rotate-right", "flip-horizontal", "flip-vertical"];

    function _(t) {
        return "string" == typeof t
    }
    var G = Number.isNaN || n.isNaN;

    function J(t) {
        return "number" == typeof t && !G(t)
    }

    function Q(t) {
        return void 0 === t
    }

    function tt(t) {
        return "object" === i(t) && null !== t
    }
    var et = Object.prototype.hasOwnProperty;

    function it(t) {
        if (!tt(t)) return !1;
        try {
            var e = t.constructor,
                i = e.prototype;
            return e && i && et.call(i, "isPrototypeOf")
        } catch (t) {
            return !1
        }
    }

    function nt(t) {
        return "function" == typeof t
    }

    function ot(e, i) {
        if (e && nt(i))
            if (Array.isArray(e) || J(e.length))
                for (var t = e.length, n = 0; n < t && !1 !== i.call(e, e[n], n, e); n += 1);
            else tt(e) && Object.keys(e)
                .forEach(function(t) {
                    i.call(e, e[t], t, e)
                });
        return e
    }
    var st = Object.assign || function(i) {
            for (var t = arguments.length, e = new Array(1 < t ? t - 1 : 0), n = 1; n < t; n++) e[n - 1] = arguments[n];
            return tt(i) && 0 < e.length && e.forEach(function(e) {
                tt(e) && Object.keys(e)
                    .forEach(function(t) {
                        i[t] = e[t]
                    })
            }), i
        },
        at = /^(?:width|height|left|top|marginLeft|marginTop)$/;

    function rt(t, e) {
        var i = t.style;
        ot(e, function(t, e) {
            at.test(e) && J(t) && (t += "px"), i[e] = t
        })
    }

    function ht(t, e) {
        return t && e && (t.classList ? t.classList.contains(e) : -1 < t.className.indexOf(e))
    }

    function lt(t, e) {
        var i;
        t && e && (J(t.length) ? ot(t, function(t) {
            lt(t, e)
        }) : t.classList ? t.classList.add(e) : (i = t.className.trim()) ? i.indexOf(e) < 0 && (t.className = "".concat(i, " ")
            .concat(e)) : t.className = e)
    }

    function ct(t, e) {
        t && e && (J(t.length) ? ot(t, function(t) {
            ct(t, e)
        }) : t.classList ? t.classList.remove(e) : 0 <= t.className.indexOf(e) && (t.className = t.className.replace(e, "")))
    }

    function ut(t, e, i) {
        e && (J(t.length) ? ot(t, function(t) {
            ut(t, e, i)
        }) : (i ? lt : ct)(t, e))
    }
    var dt = /([a-z\d])([A-Z])/g;

    function mt(t) {
        return t.replace(dt, "$1-$2")
            .toLowerCase()
    }

    function gt(t, e) {
        return tt(t[e]) ? t[e] : t.dataset ? t.dataset[e] : t.getAttribute("data-".concat(mt(e)))
    }

    function ft(t, e, i) {
        tt(i) ? t[e] = i : t.dataset ? t.dataset[e] = i : t.setAttribute("data-".concat(mt(e)), i)
    }
    var vt, pt, bt = (pt = !1, t && (vt = !1, St = function() {}, Ot = Object.defineProperty({}, "once", {
        get: function() {
            return pt = !0, vt
        },
        set: function(t) {
            vt = t
        }
    }), n.addEventListener("test", St, Ot), n.removeEventListener("test", St, Ot)), pt);

    function wt(i, t, n, e) {
        var o = 3 < arguments.length && void 0 !== e ? e : {},
            s = n;
        t.trim()
            .split(Z)
            .forEach(function(t) {
                var e;
                bt || (e = i.listeners) && e[t] && e[t][n] && (s = e[t][n], delete e[t][n], 0 === Object.keys(e[t])
                    .length && delete e[t], 0 === Object.keys(e)
                    .length && delete i.listeners), i.removeEventListener(t, s, o)
            })
    }

    function yt(s, t, a, e) {
        var r = 3 < arguments.length && void 0 !== e ? e : {},
            h = a;
        t.trim()
            .split(Z)
            .forEach(function(n) {
                var t, o;
                r.once && !bt && (t = s.listeners, h = function() {
                    delete o[n][a], s.removeEventListener(n, h, r);
                    for (var t = arguments.length, e = new Array(t), i = 0; i < t; i++) e[i] = arguments[i];
                    a.apply(s, e)
                }, (o = void 0 === t ? {} : t)[n] || (o[n] = {}), o[n][a] && s.removeEventListener(n, o[n][a], r), o[n][a] = h, s.listeners = o), s.addEventListener(n, h, r)
            })
    }

    function xt(t, e, i, n) {
        var o;
        return nt(Event) && nt(CustomEvent) ? o = new CustomEvent(e, l({
            bubbles: !0,
            cancelable: !0,
            detail: i
        }, n)) : (o = document.createEvent("CustomEvent"))
            .initCustomEvent(e, !0, !0, i), t.dispatchEvent(o)
    }

    function kt(t) {
        var e = t.rotate,
            i = t.scaleX,
            n = t.scaleY,
            o = t.translateX,
            s = t.translateY,
            t = [];
        J(o) && 0 !== o && t.push("translateX(".concat(o, "px)")), J(s) && 0 !== s && t.push("translateY(".concat(s, "px)")), J(e) && 0 !== e && t.push("rotate(".concat(e, "deg)")), J(i) && 1 !== i && t.push("scaleX(".concat(i, ")")), J(n) && 1 !== n && t.push("scaleY(".concat(n, ")"));
        t = t.length ? t.join(" ") : "none";
        return {
            WebkitTransform: t,
            msTransform: t,
            transform: t
        }
    }
    var zt = n.navigator && /(Macintosh|iPhone|iPod|iPad).*AppleWebKit/i.test(n.navigator.userAgent);

    function Dt(i, t, e) {
        var n = document.createElement("img");
        if (i.naturalWidth && !zt) return e(i.naturalWidth, i.naturalHeight), n;
        var o = document.body || document.documentElement;
        return n.onload = function() {
            e(n.width, n.height), zt || o.removeChild(n)
        }, ot(t.inheritedAttributes, function(t) {
            var e = i.getAttribute(t);
            null !== e && n.setAttribute(t, e)
        }), n.src = i.src, zt || (n.style.cssText = "left:0;max-height:none!important;max-width:none!important;min-height:0!important;min-width:0!important;opacity:0;position:absolute;top:0;z-index:-1;", o.appendChild(n)), n
    }

    function Tt(t) {
        switch (t) {
            case 2:
                return k;
            case 3:
                return x;
            case 4:
                return m;
            default:
                return ""
        }
    }

    function Et(t, e) {
        var i = t.pageX,
            n = t.pageY,
            t = {
                endX: i,
                endY: n
            };
        return e ? t : l({
            timeStamp: Date.now(),
            startX: i,
            startY: n
        }, t)
    }
    var It, At = {
            render: function() {
                this.initContainer(), this.initViewer(), this.initList(), this.renderViewer()
            },
            initBody: function() {
                var t = this.element.ownerDocument,
                    e = t.body || t.documentElement;
                this.body = e, this.scrollbarWidth = window.innerWidth - t.documentElement.clientWidth, this.initialBodyPaddingRight = e.style.paddingRight, this.initialBodyComputedPaddingRight = window.getComputedStyle(e)
                    .paddingRight
            },
            initContainer: function() {
                this.containerData = {
                    width: window.innerWidth,
                    height: window.innerHeight
                }
            },
            initViewer: function() {
                var t, e = this.options,
                    i = this.parent;
                e.inline && (t = {
                    width: Math.max(i.offsetWidth, e.minWidth),
                    height: Math.max(i.offsetHeight, e.minHeight)
                }, this.parentData = t), !this.fulled && t || (t = this.containerData), this.viewerData = st({}, t)
            },
            renderViewer: function() {
                this.options.inline && !this.fulled && rt(this.viewer, this.viewerData)
            },
            initList: function() {
                var r = this,
                    t = this.element,
                    h = this.options,
                    l = this.list,
                    c = [];
                l.innerHTML = "", ot(this.images, function(i, t) {
                    var e, n, o = i.src,
                        s = i.alt || (_(e = o) ? decodeURIComponent(e.replace(/^.*\//, "")
                            .replace(/[?&#].*$/, "")) : ""),
                        a = r.getImageURL(i);
                    (o || a) && (e = document.createElement("li"), n = document.createElement("img"), ot(h.inheritedAttributes, function(t) {
                        var e = i.getAttribute(t);
                        null !== e && n.setAttribute(t, e)
                    }), n.src = o || a, n.alt = s, n.setAttribute("data-original-url", a || o), e.setAttribute("data-index", t), e.setAttribute("data-viewer-action", "view"), e.setAttribute("role", "button"), h.keyboard && e.setAttribute("tabindex", 0), e.appendChild(n), l.appendChild(e), c.push(e))
                }), ot(this.items = c, function(e) {
                    var t, i, n = e.firstElementChild;
                    ft(n, "filled", !0), h.loading && lt(e, T), yt(n, N, t = function(t) {
                        wt(n, Y, i), h.loading && ct(e, T), r.loadImage(t)
                    }, {
                        once: !0
                    }), yt(n, Y, i = function() {
                        wt(n, N, t), h.loading && ct(e, T)
                    }, {
                        once: !0
                    })
                }), h.transition && yt(t, V, function() {
                    lt(l, S)
                }, {
                    once: !0
                })
            },
            renderList: function() {
                var t, e, i = this.index,
                    n = this.items[i];
                n && (e = n.nextElementSibling, t = parseInt(window.getComputedStyle(e || n)
                    .marginLeft, 10), e = n.offsetWidth, rt(this.list, st({
                    width: (n = e + t) * this.length - t
                }, kt({
                    translateX: (this.viewerData.width - e) / 2 - n * i
                }))))
            },
            resetList: function() {
                var t = this.list;
                t.innerHTML = "", ct(t, S), rt(t, kt({
                    translateX: 0
                }))
            },
            initImage: function(r) {
                var t, h = this,
                    l = this.options,
                    e = this.image,
                    i = this.viewerData,
                    n = this.footer.offsetHeight,
                    c = i.width,
                    u = Math.max(i.height - n, n),
                    d = this.imageData || {};
                this.imageInitializing = {
                    abort: function() {
                        t.onload = null
                    }
                }, t = Dt(e, l, function(t, e) {
                    var i = t / e,
                        n = c,
                        o = u;
                    h.imageInitializing = !1, c < u * i ? o = c / i : n = u * i;
                    var n = Math.min(.9 * n, t),
                        o = Math.min(.9 * o, e),
                        s = (c - n) / 2,
                        a = (u - o) / 2,
                        t = {
                            left: s,
                            top: a,
                            x: s,
                            y: a,
                            width: n,
                            height: o,
                            oldRatio: 1,
                            ratio: n / t,
                            aspectRatio: i,
                            naturalWidth: t,
                            naturalHeight: e
                        },
                        e = st({}, t);
                    l.rotatable && (t.rotate = d.rotate || 0, e.rotate = 0), l.scalable && (t.scaleX = d.scaleX || 1, t.scaleY = d.scaleY || 1, e.scaleX = 1, e.scaleY = 1), h.imageData = t, h.initialImageData = e, r && r()
                })
            },
            renderImage: function(t) {
                var e, i = this,
                    n = this.image,
                    o = this.imageData;
                rt(n, st({
                    width: o.width,
                    height: o.height,
                    marginLeft: o.x,
                    marginTop: o.y
                }, kt(o))), t && ((this.viewing || this.moving || this.rotating || this.scaling || this.zooming) && this.options.transition && ht(n, S) ? (e = function() {
                    i.imageRendering = !1, t()
                }, this.imageRendering = {
                    abort: function() {
                        wt(n, W, e)
                    }
                }, yt(n, W, e, {
                    once: !0
                })) : t())
            },
            resetImage: function() {
                var t;
                (this.viewing || this.viewed) && (t = this.image, this.viewing && this.viewing.abort(), t.parentNode.removeChild(t), this.image = null)
            }
        },
        r = {
            bind: function() {
                var t = this.options,
                    e = this.viewer,
                    i = this.canvas,
                    n = this.element.ownerDocument;
                yt(e, O, this.onClick = this.click.bind(this)), yt(e, L, this.onDragStart = this.dragstart.bind(this)), yt(i, X, this.onPointerDown = this.pointerdown.bind(this)), yt(n, M, this.onPointerMove = this.pointermove.bind(this)), yt(n, q, this.onPointerUp = this.pointerup.bind(this)), yt(n, F, this.onKeyDown = this.keydown.bind(this)), yt(window, P, this.onResize = this.resize.bind(this)), t.zoomable && t.zoomOnWheel && yt(e, j, this.onWheel = this.wheel.bind(this), {
                    passive: !1,
                    capture: !0
                }), t.toggleOnDblclick && yt(i, C, this.onDblclick = this.dblclick.bind(this))
            },
            unbind: function() {
                var t = this.options,
                    e = this.viewer,
                    i = this.canvas,
                    n = this.element.ownerDocument;
                wt(e, O, this.onClick), wt(e, L, this.onDragStart), wt(i, X, this.onPointerDown), wt(n, M, this.onPointerMove), wt(n, q, this.onPointerUp), wt(n, F, this.onKeyDown), wt(window, P, this.onResize), t.zoomable && t.zoomOnWheel && wt(e, j, this.onWheel, {
                    passive: !1,
                    capture: !0
                }), t.toggleOnDblclick && wt(i, C, this.onDblclick)
            }
        },
        t = {
            click: function(t) {
                var e = this.options,
                    i = this.imageData,
                    n = t.target,
                    o = gt(n, K);
                switch (o || "img" !== n.localName || "li" !== n.parentElement.localName || (o = gt(n = n.parentElement, K)), a && t.isTrusted && n === this.canvas && clearTimeout(this.clickCanvasTimeout), o) {
                    case "mix":
                        this.played ? this.stop() : e.inline ? this.fulled ? this.exit() : this.full() : this.hide();
                        break;
                    case "hide":
                        this.hide();
                        break;
                    case "view":
                        this.view(gt(n, "index"));
                        break;
                    case "zoom-in":
                        this.zoom(.1, !0);
                        break;
                    case "zoom-out":
                        this.zoom(-.1, !0);
                        break;
                    case "one-to-one":
                        this.toggle();
                        break;
                    case "reset":
                        this.reset();
                        break;
                    case "prev":
                        this.prev(e.loop);
                        break;
                    case "play":
                        this.play(e.fullscreen);
                        break;
                    case "next":
                        this.next(e.loop);
                        break;
                    case "rotate-left":
                        this.rotate(-90);
                        break;
                    case "rotate-right":
                        this.rotate(90);
                        break;
                    case "flip-horizontal":
                        this.scaleX(-i.scaleX || -1);
                        break;
                    case "flip-vertical":
                        this.scaleY(-i.scaleY || -1);
                        break;
                    default:
                        this.played && this.stop()
                }
            },
            dblclick: function(t) {
                t.preventDefault(), this.viewed && t.target === this.image && (a && t.isTrusted && clearTimeout(this.doubleClickImageTimeout), this.toggle(t))
            },
            load: function() {
                var t = this;
                this.timeout && (clearTimeout(this.timeout), this.timeout = !1);
                var e = this.element,
                    i = this.options,
                    n = this.image,
                    o = this.index,
                    s = this.viewerData;
                ct(n, D), i.loading && ct(this.canvas, T), n.style.cssText = "height:0;" + "margin-left:".concat(s.width / 2, "px;") + "margin-top:".concat(s.height / 2, "px;") + "max-width:none!important;position:relative;width:0;", this.initImage(function() {
                    ut(n, E, i.movable), ut(n, S, i.transition), t.renderImage(function() {
                        t.viewed = !0, t.viewing = !1, nt(i.viewed) && yt(e, V, i.viewed, {
                            once: !0
                        }), xt(e, V, {
                            originalImage: t.images[o],
                            index: o,
                            image: n
                        }, {
                            cancelable: !1
                        })
                    })
                })
            },
            loadImage: function(t) {
                var n = t.target,
                    t = n.parentNode,
                    o = t.offsetWidth || 30,
                    s = t.offsetHeight || 50,
                    a = !!gt(n, "filled");
                Dt(n, this.options, function(t, e) {
                    var i = t / e,
                        t = o,
                        e = s;
                    o < s * i ? a ? t = s * i : e = o / i : a ? e = o / i : t = s * i, rt(n, st({
                        width: t,
                        height: e
                    }, kt({
                        translateX: (o - t) / 2,
                        translateY: (s - e) / 2
                    })))
                })
            },
            keydown: function(t) {
                var e = this.options;
                if (e.keyboard) {
                    var i = t.keyCode || t.which || t.charCode;
                    if (13 === i && this.viewer.contains(t.target) && this.click(t), this.fulled) switch (i) {
                        case 27:
                            this.played ? this.stop() : e.inline ? this.fulled && this.exit() : this.hide();
                            break;
                        case 32:
                            this.played && this.stop();
                            break;
                        case 37:
                            this.prev(e.loop);
                            break;
                        case 38:
                            t.preventDefault(), this.zoom(e.zoomRatio, !0);
                            break;
                        case 39:
                            this.next(e.loop);
                            break;
                        case 40:
                            t.preventDefault(), this.zoom(-e.zoomRatio, !0);
                            break;
                        case 48:
                        case 49:
                            t.ctrlKey && (t.preventDefault(), this.toggle())
                    }
                }
            },
            dragstart: function(t) {
                "img" === t.target.localName && t.preventDefault()
            },
            pointerdown: function(t) {
                var e = this.options,
                    i = this.pointers,
                    n = t.buttons,
                    o = t.button;
                !this.viewed || this.showing || this.viewing || this.hiding || ("mousedown" === t.type || "pointerdown" === t.type && "mouse" === t.pointerType) && (J(n) && 1 !== n || J(o) && 0 !== o || t.ctrlKey) || (t.preventDefault(), t.changedTouches ? ot(t.changedTouches, function(t) {
                    i[t.identifier] = Et(t)
                }) : i[t.pointerId || 0] = Et(t), o = !!e.movable && c, e.zoomOnTouch && e.zoomable && 1 < Object.keys(i)
                    .length ? o = d : e.slideOnTouch && ("touch" === t.pointerType || "touchstart" === t.type) && this.isSwitchable() && (o = u), !e.transition || o !== c && o !== d || ct(this.image, S), this.action = o)
            },
            pointermove: function(t) {
                var e = this.pointers,
                    i = this.action;
                this.viewed && i && (t.preventDefault(), t.changedTouches ? ot(t.changedTouches, function(t) {
                    st(e[t.identifier] || {}, Et(t, !0))
                }) : st(e[t.pointerId || 0] || {}, Et(t, !0)), this.change(t))
            },
            pointerup: function(t) {
                var e, i = this,
                    n = this.options,
                    o = this.action,
                    s = this.pointers;
                t.changedTouches ? ot(t.changedTouches, function(t) {
                    e = s[t.identifier], delete s[t.identifier]
                }) : (e = s[t.pointerId || 0], delete s[t.pointerId || 0]), o && (t.preventDefault(), !n.transition || o !== c && o !== d || lt(this.image, S), this.action = !1, a && o !== d && e && Date.now() - e.timeStamp < 500 && (clearTimeout(this.clickCanvasTimeout), clearTimeout(this.doubleClickImageTimeout), n.toggleOnDblclick && this.viewed && t.target === this.image ? this.imageClicked ? (this.imageClicked = !1, this.doubleClickImageTimeout = setTimeout(function() {
                    xt(i.image, C)
                }, 50)) : (this.imageClicked = !0, this.doubleClickImageTimeout = setTimeout(function() {
                    i.imageClicked = !1
                }, 500)) : (this.imageClicked = !1, n.backdrop && "static" !== n.backdrop && t.target === this.canvas && (this.clickCanvasTimeout = setTimeout(function() {
                    xt(i.canvas, O)
                }, 50)))))
            },
            resize: function() {
                var e = this;
                this.isShown && !this.hiding && (this.fulled && (this.close(), this.initBody(), this.open()), this.initContainer(), this.initViewer(), this.renderViewer(), this.renderList(), this.viewed && this.initImage(function() {
                    e.renderImage()
                }), this.played && (!this.options.fullscreen || !this.fulled || document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement ? ot(this.player.getElementsByTagName("img"), function(t) {
                    yt(t, N, e.loadImage.bind(e), {
                        once: !0
                    }), xt(t, N)
                }) : this.stop()))
            },
            wheel: function(t) {
                var e, i, n = this;
                this.viewed && (t.preventDefault(), this.wheeling || (this.wheeling = !0, setTimeout(function() {
                    n.wheeling = !1
                }, 50), e = Number(this.options.zoomRatio) || .1, i = 1, t.deltaY ? i = 0 < t.deltaY ? 1 : -1 : t.wheelDelta ? i = -t.wheelDelta / 120 : t.detail && (i = 0 < t.detail ? 1 : -1), this.zoom(-i * e, !0, t)))
            }
        },
        St = {
            show: function() {
                var t = 0 < arguments.length && void 0 !== arguments[0] && arguments[0],
                    e = this.element,
                    i = this.options;
                if (i.inline || this.showing || this.isShown || this.showing) return this;
                if (!this.ready) return this.build(), this.ready && this.show(t), this;
                if (nt(i.show) && yt(e, B, i.show, {
                    once: !0
                }), !1 === xt(e, B) || !this.ready) return this;
                this.hiding && this.transitioning.abort(), this.showing = !0, this.open();
                var n, o = this.viewer;
                return ct(o, y), o.setAttribute("role", "dialog"), o.setAttribute("aria-labelledby", this.title.id), o.setAttribute("aria-modal", !0), o.removeAttribute("aria-hidden"), i.transition && !t ? (n = this.shown.bind(this), this.transitioning = {
                    abort: function() {
                        wt(o, W, n), ct(o, z)
                    }
                }, lt(o, S), o.initialOffsetWidth = o.offsetWidth, yt(o, W, n, {
                    once: !0
                }), lt(o, z)) : (lt(o, z), this.shown()), this
            },
            hide: function() {
                var i = this,
                    t = 0 < arguments.length && void 0 !== arguments[0] && arguments[0],
                    e = this.element,
                    n = this.options;
                if (n.inline || this.hiding || !this.isShown && !this.showing) return this;
                if (nt(n.hide) && yt(e, "hide", n.hide, {
                    once: !0
                }), !1 === xt(e, "hide")) return this;
                this.showing && this.transitioning.abort(), this.hiding = !0, this.played ? this.stop() : this.viewing && this.viewing.abort();
                var o, s, a = this.viewer,
                    r = this.image,
                    h = function() {
                        ct(a, z), i.hidden()
                    };
                return n.transition && !t ? (o = function t(e) {
                    e && e.target === a && (wt(a, W, t), i.hidden())
                }, s = function() {
                    ht(a, S) ? (yt(a, W, o), ct(a, z)) : h()
                }, this.transitioning = {
                    abort: function() {
                        i.viewed && ht(r, S) ? wt(r, W, s) : ht(a, S) && wt(a, W, o)
                    }
                }, this.viewed && ht(r, S) ? (yt(r, W, s, {
                    once: !0
                }), this.zoomTo(0, !1, null, !0)) : s()) : h(), this
            },
            view: function() {
                var i = this,
                    t = 0 < arguments.length && void 0 !== arguments[0] ? arguments[0] : this.options.initialViewIndex,
                    t = Number(t) || 0;
                if (this.hiding || this.played || t < 0 || t >= this.length || this.viewed && t === this.index) return this;
                if (!this.isShown) return this.index = t, this.show();
                this.viewing && this.viewing.abort();
                var e = this.element,
                    n = this.options,
                    o = this.title,
                    s = this.canvas,
                    a = this.items[t],
                    r = a.querySelector("img"),
                    h = gt(r, "originalUrl"),
                    l = r.getAttribute("alt"),
                    c = document.createElement("img");
                if (ot(n.inheritedAttributes, function(t) {
                    var e = r.getAttribute(t);
                    null !== e && c.setAttribute(t, e)
                }), c.src = h, c.alt = l, nt(n.view) && yt(e, "view", n.view, {
                    once: !0
                }), !1 === xt(e, "view", {
                    originalImage: this.images[t],
                    index: t,
                    image: c
                }) || !this.isShown || this.hiding || this.played) return this;
                h = this.items[this.index];
                h && (ct(h, f), h.removeAttribute("aria-selected")), lt(a, f), a.setAttribute("aria-selected", !0), n.focus && a.focus(), this.image = c, this.viewed = !1, this.index = t, this.imageData = {}, lt(c, D), n.loading && lt(s, T), s.innerHTML = "", s.appendChild(c), this.renderList(), o.innerHTML = "";

                function u() {
                    var t = i.imageData,
                        e = Array.isArray(n.title) ? n.title[1] : n.title;
                    o.innerHTML = _(t = nt(e) ? e.call(i, c, t) : "".concat(l, " (")
                        .concat(t.naturalWidth, " × ")
                        .concat(t.naturalHeight, ")")) ? t.replace(/&(?!amp;|quot;|#39;|lt;|gt;)/g, "&amp;")
                        .replace(/"/g, "&quot;")
                        .replace(/'/g, "&#39;")
                        .replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;") : t
                }
                var d, m;
                return yt(e, V, u, {
                    once: !0
                }), this.viewing = {
                    abort: function() {
                        wt(e, V, u), c.complete ? i.imageRendering ? i.imageRendering.abort() : i.imageInitializing && i.imageInitializing.abort() : (c.src = "", wt(c, N, d), i.timeout && clearTimeout(i.timeout))
                    }
                }, c.complete ? this.load() : (yt(c, N, d = function() {
                    wt(c, Y, m), i.load()
                }, {
                    once: !0
                }), yt(c, Y, m = function() {
                    wt(c, N, d), i.timeout && (clearTimeout(i.timeout), i.timeout = !1), ct(c, D), n.loading && ct(i.canvas, T)
                }, {
                    once: !0
                }), this.timeout && clearTimeout(this.timeout), this.timeout = setTimeout(function() {
                    ct(c, D), i.timeout = !1
                }, 1e3)), this
            },
            prev: function() {
                var t = this.index - 1;
                return t < 0 && (t = 0 < arguments.length && void 0 !== arguments[0] && arguments[0] ? this.length - 1 : 0), this.view(t), this
            },
            next: function() {
                var t = this.length - 1,
                    e = this.index + 1;
                return this.view(e = t < e ? 0 < arguments.length && void 0 !== arguments[0] && arguments[0] ? 0 : t : e), this
            },
            move: function(t) {
                var e = 1 < arguments.length && void 0 !== arguments[1] ? arguments[1] : t,
                    i = this.imageData;
                return this.moveTo(Q(t) ? t : i.x + Number(t), Q(e) ? e : i.y + Number(e)), this
            },
            moveTo: function(t) {
                var e = this,
                    i = 1 < arguments.length && void 0 !== arguments[1] ? arguments[1] : t,
                    n = 2 < arguments.length && void 0 !== arguments[2] ? arguments[2] : null,
                    o = this.element,
                    s = this.options,
                    a = this.imageData;
                if (t = Number(t), i = Number(i), this.viewed && !this.played && s.movable) {
                    var r = a.x,
                        h = a.y,
                        l = !1;
                    if (J(t) ? l = !0 : t = r, J(i) ? l = !0 : i = h, l) {
                        if (nt(s.move) && yt(o, "move", s.move, {
                            once: !0
                        }), !1 === xt(o, "move", {
                            x: t,
                            y: i,
                            oldX: r,
                            oldY: h,
                            originalEvent: n
                        })) return this;
                        a.x = t, a.y = i, a.left = t, a.top = i, this.moving = !0, this.renderImage(function() {
                            e.moving = !1, nt(s.moved) && yt(o, "moved", s.moved, {
                                once: !0
                            }), xt(o, "moved", {
                                x: t,
                                y: i,
                                oldX: r,
                                oldY: h,
                                originalEvent: n
                            }, {
                                cancelable: !1
                            })
                        })
                    }
                }
                return this
            },
            rotate: function(t) {
                return this.rotateTo((this.imageData.rotate || 0) + Number(t)), this
            },
            rotateTo: function(t) {
                var e = this,
                    i = this.element,
                    n = this.options,
                    o = this.imageData;
                if (J(t = Number(t)) && this.viewed && !this.played && n.rotatable) {
                    var s = o.rotate;
                    if (nt(n.rotate) && yt(i, "rotate", n.rotate, {
                        once: !0
                    }), !1 === xt(i, "rotate", {
                        degree: t,
                        oldDegree: s
                    })) return this;
                    o.rotate = t, this.rotating = !0, this.renderImage(function() {
                        e.rotating = !1, nt(n.rotated) && yt(i, U, n.rotated, {
                            once: !0
                        }), xt(i, U, {
                            degree: t,
                            oldDegree: s
                        }, {
                            cancelable: !1
                        })
                    })
                }
                return this
            },
            scaleX: function(t) {
                return this.scale(t, this.imageData.scaleY), this
            },
            scaleY: function(t) {
                return this.scale(this.imageData.scaleX, t), this
            },
            scale: function(t) {
                var e = this,
                    i = 1 < arguments.length && void 0 !== arguments[1] ? arguments[1] : t,
                    n = this.element,
                    o = this.options,
                    s = this.imageData;
                if (t = Number(t), i = Number(i), this.viewed && !this.played && o.scalable) {
                    var a = s.scaleX,
                        r = s.scaleY,
                        h = !1;
                    if (J(t) ? h = !0 : t = a, J(i) ? h = !0 : i = r, h) {
                        if (nt(o.scale) && yt(n, "scale", o.scale, {
                            once: !0
                        }), !1 === xt(n, "scale", {
                            scaleX: t,
                            scaleY: i,
                            oldScaleX: a,
                            oldScaleY: r
                        })) return this;
                        s.scaleX = t, s.scaleY = i, this.scaling = !0, this.renderImage(function() {
                            e.scaling = !1, nt(o.scaled) && yt(n, "scaled", o.scaled, {
                                once: !0
                            }), xt(n, "scaled", {
                                scaleX: t,
                                scaleY: i,
                                oldScaleX: a,
                                oldScaleY: r
                            }, {
                                cancelable: !1
                            })
                        })
                    }
                }
                return this
            },
            zoom: function(t) {
                var e = 1 < arguments.length && void 0 !== arguments[1] && arguments[1],
                    i = 2 < arguments.length && void 0 !== arguments[2] ? arguments[2] : null,
                    n = this.imageData;
                return t = Number(t), this.zoomTo(n.width * (t = t < 0 ? 1 / (1 - t) : 1 + t) / n.naturalWidth, e, i), this
            },
            zoomTo: function(t) {
                var i, n, o, e = this,
                    s = 1 < arguments.length && void 0 !== arguments[1] && arguments[1],
                    a = 2 < arguments.length && void 0 !== arguments[2] ? arguments[2] : null,
                    r = 3 < arguments.length && void 0 !== arguments[3] && arguments[3],
                    h = this.element,
                    l = this.options,
                    c = this.pointers,
                    u = this.imageData,
                    d = u.x,
                    m = u.y,
                    g = u.width,
                    f = u.height,
                    v = u.naturalWidth,
                    p = u.naturalHeight;
                if (J(t = Math.max(0, t)) && this.viewed && !this.played && (r || l.zoomable)) {
                    r || (b = Math.max(.01, l.minZoomRatio), w = Math.min(100, l.maxZoomRatio), t = Math.min(Math.max(t, b), w));
                    var r = v * (t = a && .055 <= l.zoomRatio && .95 < t && t < 1.05 ? 1 : t),
                        b = p * t,
                        w = r - g,
                        v = b - f,
                        y = u.ratio;
                    if (nt(l.zoom) && yt(h, "zoom", l.zoom, {
                        once: !0
                    }), !1 === xt(h, "zoom", {
                        ratio: t,
                        oldRatio: y,
                        originalEvent: a
                    })) return this;
                    this.zooming = !0, a ? (p = {
                        left: (p = (p = this.viewer)
                            .getBoundingClientRect())
                            .left + (window.pageXOffset - document.documentElement.clientLeft),
                        top: p.top + (window.pageYOffset - document.documentElement.clientTop)
                    }, c = c && Object.keys(c)
                        .length ? (o = n = i = 0, ot(c, function(t) {
                        var e = t.startX,
                            t = t.startY;
                        i += e, n += t, o += 1
                    }), {
                        pageX: i /= o,
                        pageY: n /= o
                    }) : {
                        pageX: a.pageX,
                        pageY: a.pageY
                    }, u.x -= (c.pageX - p.left - d) / g * w, u.y -= (c.pageY - p.top - m) / f * v) : (u.x -= w / 2, u.y -= v / 2), u.left = u.x, u.top = u.y, u.width = r, u.height = b, u.oldRatio = y, u.ratio = t, this.renderImage(function() {
                        e.zooming = !1, nt(l.zoomed) && yt(h, "zoomed", l.zoomed, {
                            once: !0
                        }), xt(h, "zoomed", {
                            ratio: t,
                            oldRatio: y,
                            originalEvent: a
                        }, {
                            cancelable: !1
                        })
                    }), s && this.tooltip()
                }
                return this
            },
            play: function() {
                var e = this,
                    t = 0 < arguments.length && void 0 !== arguments[0] && arguments[0];
                if (!this.isShown || this.played) return this;
                var i = this.element,
                    o = this.options;
                if (nt(o.play) && yt(i, "play", o.play, {
                    once: !0
                }), !1 === xt(i, "play")) return this;
                var s = this.player,
                    a = this.loadImage.bind(this),
                    r = [],
                    h = 0,
                    l = 0;
                return this.played = !0, this.onLoadWhenPlay = a, t && this.requestFullscreen(t), lt(s, A), ot(this.items, function(t, e) {
                    var i = t.querySelector("img"),
                        n = document.createElement("img");
                    n.src = gt(i, "originalUrl"), n.alt = i.getAttribute("alt"), n.referrerPolicy = i.referrerPolicy, h += 1, lt(n, p), ut(n, S, o.transition), ht(t, f) && (lt(n, z), l = e), r.push(n), yt(n, N, a, {
                        once: !0
                    }), s.appendChild(n)
                }), J(o.interval) && 0 < o.interval && 1 < h && function t() {
                    e.playing = setTimeout(function() {
                        ct(r[l], z), lt(r[l = (l += 1) < h ? l : 0], z), t()
                    }, o.interval)
                }(), this
            },
            stop: function() {
                var e = this;
                if (!this.played) return this;
                var t = this.element,
                    i = this.options;
                if (nt(i.stop) && yt(t, "stop", i.stop, {
                    once: !0
                }), !1 === xt(t, "stop")) return this;
                t = this.player;
                return this.played = !1, clearTimeout(this.playing), ot(t.getElementsByTagName("img"), function(t) {
                    wt(t, N, e.onLoadWhenPlay)
                }), ct(t, A), t.innerHTML = "", this.exitFullscreen(), this
            },
            full: function() {
                var t = this,
                    e = this.options,
                    i = this.viewer,
                    n = this.image,
                    o = this.list;
                return !this.isShown || this.played || this.fulled || !e.inline || (this.fulled = !0, this.open(), lt(this.button, h), e.transition && (ct(o, S), this.viewed && ct(n, S)), lt(i, b), i.setAttribute("role", "dialog"), i.setAttribute("aria-labelledby", this.title.id), i.setAttribute("aria-modal", !0), i.removeAttribute("style"), rt(i, {
                    zIndex: e.zIndex
                }), e.focus && this.enforceFocus(), this.initContainer(), this.viewerData = st({}, this.containerData), this.renderList(), this.viewed && this.initImage(function() {
                    t.renderImage(function() {
                        e.transition && setTimeout(function() {
                            lt(n, S), lt(o, S)
                        }, 0)
                    })
                })), this
            },
            exit: function() {
                var t = this,
                    e = this.options,
                    i = this.viewer,
                    n = this.image,
                    o = this.list;
                return this.isShown && !this.played && this.fulled && e.inline && (this.fulled = !1, this.close(), ct(this.button, h), e.transition && (ct(o, S), this.viewed && ct(n, S)), e.focus && this.clearEnforceFocus(), i.removeAttribute("role"), i.removeAttribute("aria-labelledby"), i.removeAttribute("aria-modal"), ct(i, b), rt(i, {
                    zIndex: e.zIndexInline
                }), this.viewerData = st({}, this.parentData), this.renderViewer(), this.renderList(), this.viewed && this.initImage(function() {
                    t.renderImage(function() {
                        e.transition && setTimeout(function() {
                            lt(n, S), lt(o, S)
                        }, 0)
                    })
                })), this
            },
            tooltip: function() {
                var t = this,
                    e = this.options,
                    i = this.tooltipBox,
                    n = this.imageData;
                return this.viewed && !this.played && e.tooltip && (i.textContent = "".concat(Math.round(100 * n.ratio), "%"), this.tooltipping ? clearTimeout(this.tooltipping) : e.transition ? (this.fading && xt(i, W), lt(i, A), lt(i, p), lt(i, S), i.removeAttribute("aria-hidden"), i.initialOffsetWidth = i.offsetWidth, lt(i, z)) : (lt(i, A), i.removeAttribute("aria-hidden")), this.tooltipping = setTimeout(function() {
                    e.transition ? (yt(i, W, function() {
                        ct(i, A), ct(i, p), ct(i, S), i.setAttribute("aria-hidden", !0), t.fading = !1
                    }, {
                        once: !0
                    }), ct(i, z), t.fading = !0) : (ct(i, A), i.setAttribute("aria-hidden", !0)), t.tooltipping = !1
                }, 1e3)), this
            },
            toggle: function() {
                var t = 0 < arguments.length && void 0 !== arguments[0] ? arguments[0] : null;
                return 1 === this.imageData.ratio ? this.zoomTo(this.imageData.oldRatio, !0, t) : this.zoomTo(1, !0, t), this
            },
            reset: function() {
                return this.viewed && !this.played && (this.imageData = st({}, this.initialImageData), this.renderImage()), this
            },
            update: function() {
                var e = this,
                    t = this.element,
                    i = this.options,
                    n = this.isImg;
                if (n && !t.parentNode) return this.destroy();
                var o, s = [];
                return ot(n ? [t] : t.querySelectorAll("img"), function(t) {
                    nt(i.filter) ? i.filter.call(e, t) && s.push(t) : e.getImageURL(t) && s.push(t)
                }), s.length && (this.images = s, this.length = s.length, this.ready ? (o = [], ot(this.items, function(t, e) {
                    var i = t.querySelector("img"),
                        t = s[e];
                    t && i && t.src === i.src && t.alt === i.alt || o.push(e)
                }), rt(this.list, {
                    width: "auto"
                }), this.initList(), this.isShown && (this.length ? this.viewed && (0 <= (t = o.indexOf(this.index)) ? (this.viewed = !1, this.view(Math.max(Math.min(this.index - t, this.length - 1), 0))) : (lt(t = this.items[this.index], f), t.setAttribute("aria-selected", !0))) : (this.image = null, this.viewed = !1, this.index = 0, this.imageData = {}, this.canvas.innerHTML = "", this.title.innerHTML = ""))) : this.build()), this
            },
            destroy: function() {
                var t = this.element,
                    e = this.options;
                return t[g] && (this.destroyed = !0, this.ready ? (this.played && this.stop(), e.inline ? (this.fulled && this.exit(), this.unbind()) : this.isShown ? (this.viewing && (this.imageRendering ? this.imageRendering.abort() : this.imageInitializing && this.imageInitializing.abort()), this.hiding && this.transitioning.abort(), this.hidden()) : this.showing && (this.transitioning.abort(), this.hidden()), this.ready = !1, this.viewer.parentNode.removeChild(this.viewer)) : e.inline && (this.delaying ? this.delaying.abort() : this.initializing && this.initializing.abort()), e.inline || wt(t, O, this.onStart), t[g] = void 0), this
            }
        },
        Ot = {
            getImageURL: function(t) {
                var e = this.options.url;
                return e = _(e) ? t.getAttribute(e) : nt(e) ? e.call(this, t) : ""
            },
            enforceFocus: function() {
                var i = this;
                this.clearEnforceFocus(), yt(document, R, this.onFocusin = function(t) {
                    var e = i.viewer,
                        t = t.target;
                    t === document || t === e || e.contains(t) || null !== t.getAttribute("tabindex") && "true" === t.getAttribute("aria-modal") || e.focus()
                })
            },
            clearEnforceFocus: function() {
                this.onFocusin && (wt(document, R, this.onFocusin), this.onFocusin = null)
            },
            open: function() {
                var t = this.body;
                lt(t, I), t.style.paddingRight = "".concat(this.scrollbarWidth + (parseFloat(this.initialBodyComputedPaddingRight) || 0), "px")
            },
            close: function() {
                var t = this.body;
                ct(t, I), t.style.paddingRight = this.initialBodyPaddingRight
            },
            shown: function() {
                var t = this.element,
                    e = this.options,
                    i = this.viewer;
                this.fulled = !0, this.isShown = !0, this.render(), this.bind(), this.showing = !1, e.focus && (i.focus(), this.enforceFocus()), nt(e.shown) && yt(t, "shown", e.shown, {
                    once: !0
                }), !1 !== xt(t, "shown") && this.ready && this.isShown && !this.hiding && this.view(this.index)
            },
            hidden: function() {
                var t = this.element,
                    e = this.options,
                    i = this.viewer;
                e.fucus && this.clearEnforceFocus(), this.fulled = !1, this.viewed = !1, this.isShown = !1, this.close(), this.unbind(), lt(i, y), i.removeAttribute("role"), i.removeAttribute("aria-labelledby"), i.removeAttribute("aria-modal"), i.setAttribute("aria-hidden", !0), this.resetList(), this.resetImage(), this.hiding = !1, this.destroyed || (nt(e.hidden) && yt(t, "hidden", e.hidden, {
                    once: !0
                }), xt(t, "hidden", null, {
                    cancelable: !1
                }))
            },
            requestFullscreen: function(t) {
                var e = this.element.ownerDocument;
                this.fulled && !(e.fullscreenElement || e.webkitFullscreenElement || e.mozFullScreenElement || e.msFullscreenElement) && ((e = e.documentElement)
                    .requestFullscreen ? it(t) ? e.requestFullscreen(t) : e.requestFullscreen() : e.webkitRequestFullscreen ? e.webkitRequestFullscreen(Element.ALLOW_KEYBOARD_INPUT) : e.mozRequestFullScreen ? e.mozRequestFullScreen() : e.msRequestFullscreen && e.msRequestFullscreen())
            },
            exitFullscreen: function() {
                var t = this.element.ownerDocument;
                this.fulled && (t.fullscreenElement || t.webkitFullscreenElement || t.mozFullScreenElement || t.msFullscreenElement) && (t.exitFullscreen ? t.exitFullscreen() : t.webkitExitFullscreen ? t.webkitExitFullscreen() : t.mozCancelFullScreen ? t.mozCancelFullScreen() : t.msExitFullscreen && t.msExitFullscreen())
            },
            change: function(t) {
                var e = this.options,
                    i = this.pointers,
                    n = i[Object.keys(i)[0]];
                if (n) {
                    var s, a, o = n.endX - n.startX,
                        r = n.endY - n.startY;
                    switch (this.action) {
                        case c:
                            this.move(o, r, t);
                            break;
                        case d:
                            this.zoom((s = l({}, h = i), a = [], ot(h, function(o, t) {
                                delete s[t], ot(s, function(t) {
                                    var e = Math.abs(o.startX - t.startX),
                                        i = Math.abs(o.startY - t.startY),
                                        n = Math.abs(o.endX - t.endX),
                                        t = Math.abs(o.endY - t.endY),
                                        i = Math.sqrt(e * e + i * i),
                                        t = Math.sqrt(n * n + t * t);
                                    a.push((t - i) / i)
                                })
                            }), a.sort(function(t, e) {
                                return Math.abs(t) < Math.abs(e)
                            }), a[0]), !1, t);
                            break;
                        case u:
                            this.action = "switched";
                            var h = Math.abs(o);
                            1 < h && h > Math.abs(r) && (this.pointers = {}, 1 < o ? this.prev(e.loop) : o < -1 && this.next(e.loop))
                    }
                    ot(i, function(t) {
                        t.startX = t.endX, t.startY = t.endY
                    })
                }
            },
            isSwitchable: function() {
                var t = this.imageData,
                    e = this.viewerData;
                return 1 < this.length && 0 <= t.x && 0 <= t.y && t.width <= e.width && t.height <= e.height
            }
        },
        Ct = n.Viewer,
        Lt = (It = -1, function() {
            return It += 1
        }),
        n = function() {
            function i(t) {
                var e = 1 < arguments.length && void 0 !== arguments[1] ? arguments[1] : {};
                if (! function(t, e) {
                    if (!(t instanceof e)) throw new TypeError("Cannot call a class as a function")
                }(this, i), !t || 1 !== t.nodeType) throw new Error("The first argument is required and must be an element.");
                this.element = t, this.options = st({}, s, it(e) && e), this.action = !1, this.fading = !1, this.fulled = !1, this.hiding = !1, this.imageClicked = !1, this.imageData = {}, this.index = this.options.initialViewIndex, this.isImg = !1, this.isShown = !1, this.length = 0, this.moving = !1, this.played = !1, this.playing = !1, this.pointers = {}, this.ready = !1, this.rotating = !1, this.scaling = !1, this.showing = !1, this.timeout = !1, this.tooltipping = !1, this.viewed = !1, this.viewing = !1, this.wheeling = !1, this.zooming = !1, this.id = Lt(), this.init()
            }
            var t, e, n;
            return t = i, n = [{
                key: "noConflict",
                value: function() {
                    return window.Viewer = Ct, i
                }
            }, {
                key: "setDefaults",
                value: function(t) {
                    st(s, it(t) && t)
                }
            }], (e = [{
                key: "init",
                value: function() {
                    var t, e, i, n, o = this,
                        s = this.element,
                        a = this.options;
                    s[g] || (s[g] = this, a.focus && !a.keyboard && (a.focus = !1), t = "img" === s.localName, e = [], ot(t ? [s] : s.querySelectorAll("img"), function(t) {
                        nt(a.filter) ? a.filter.call(o, t) && e.push(t) : o.getImageURL(t) && e.push(t)
                    }), this.isImg = t, this.length = e.length, this.images = e, this.initBody(), Q(document.createElement(g)
                        .style.transition) && (a.transition = !1), a.inline ? (i = 0, n = function() {
                        var t;
                        (i += 1) === o.length && (o.initializing = !1, o.delaying = {
                            abort: function() {
                                clearTimeout(t)
                            }
                        }, t = setTimeout(function() {
                            o.delaying = !1, o.build()
                        }, 0))
                    }, this.initializing = {
                        abort: function() {
                            ot(e, function(t) {
                                t.complete || wt(t, N, n)
                            })
                        }
                    }, ot(e, function(t) {
                        t.complete ? n() : yt(t, N, n, {
                            once: !0
                        })
                    })) : yt(s, O, this.onStart = function(t) {
                        t = t.target;
                        "img" !== t.localName || nt(a.filter) && !a.filter.call(o, t) || o.view(o.images.indexOf(t))
                    }))
                }
            }, {
                key: "build",
                value: function() {
                    var t, s, e, i, n, o, a, r, h, l, c, u, d, m;
                    this.ready || (t = this.element, s = this.options, e = t.parentNode, (d = document.createElement("div"))
                        .innerHTML = '<div class="viewer-container" tabindex="-1" touch-action="none"><div class="viewer-canvas"></div><div class="viewer-footer"><div class="viewer-title"></div><div class="viewer-toolbar"></div><div class="viewer-navbar"><ul class="viewer-list" role="navigation"></ul></div></div><div class="viewer-tooltip" role="alert" aria-hidden="true"></div><div class="viewer-button" data-viewer-action="mix" role="button"></div><div class="viewer-player"></div></div>', n = (i = d.querySelector(".".concat(g, "-container")))
                        .querySelector(".".concat(g, "-title")), o = i.querySelector(".".concat(g, "-toolbar")), a = i.querySelector(".".concat(g, "-navbar")), m = i.querySelector(".".concat(g, "-button")), d = i.querySelector(".".concat(g, "-canvas")), this.parent = e, this.viewer = i, this.title = n, this.toolbar = o, this.navbar = a, this.button = m, this.canvas = d, this.footer = i.querySelector(".".concat(g, "-footer")), this.tooltipBox = i.querySelector(".".concat(g, "-tooltip")), this.player = i.querySelector(".".concat(g, "-player")), this.list = i.querySelector(".".concat(g, "-list")), i.id = "".concat(g)
                        .concat(this.id), n.id = "".concat(g, "Title")
                        .concat(this.id), lt(n, s.title ? Tt(Array.isArray(s.title) ? s.title[0] : s.title) : y), lt(a, s.navbar ? Tt(s.navbar) : y), ut(m, y, !s.button), s.keyboard && m.setAttribute("tabindex", 0), s.backdrop && (lt(i, "".concat(g, "-backdrop")), s.inline || "static" === s.backdrop || ft(d, K, "hide")), _(s.className) && s.className && s.className.split(Z)
                        .forEach(function(t) {
                            lt(i, t)
                        }), s.toolbar ? (r = document.createElement("ul"), h = it(s.toolbar), l = $.slice(0, 3), c = $.slice(7, 9), u = $.slice(9), h || lt(o, Tt(s.toolbar)), ot(h ? s.toolbar : $, function(t, e) {
                        var i = h && it(t),
                            n = h ? mt(e) : t,
                            o = i && !Q(t.show) ? t.show : t;
                        !o || !s.zoomable && -1 !== l.indexOf(n) || !s.rotatable && -1 !== c.indexOf(n) || !s.scalable && -1 !== u.indexOf(n) || (e = i && !Q(t.size) ? t.size : t, i = i && !Q(t.click) ? t.click : t, t = document.createElement("li"), s.keyboard && t.setAttribute("tabindex", 0), t.setAttribute("role", "button"), lt(t, "".concat(g, "-")
                            .concat(n)), nt(i) || ft(t, K, n), J(o) && lt(t, Tt(o)), -1 !== ["small", "large"].indexOf(e) ? lt(t, "".concat(g, "-")
                            .concat(e)) : "play" === n && lt(t, "".concat(g, "-large")), nt(i) && yt(t, O, i), r.appendChild(t))
                    }), o.appendChild(r)) : lt(o, y), s.rotatable || (lt(d = o.querySelectorAll('li[class*="rotate"]'), D), ot(d, function(t) {
                        o.appendChild(t)
                    })), s.inline ? (lt(m, w), rt(i, {
                        zIndex: s.zIndexInline
                    }), "static" === window.getComputedStyle(e)
                        .position && rt(e, {
                        position: "relative"
                    }), e.insertBefore(i, t.nextSibling)) : (lt(m, v), lt(i, b), lt(i, p), lt(i, y), rt(i, {
                        zIndex: s.zIndex
                    }), (m = (m = _(m = s.container) ? t.ownerDocument.querySelector(m) : m) || this.body)
                        .appendChild(i)), s.inline && (this.render(), this.bind(), this.isShown = !0), this.ready = !0, nt(s.ready) && yt(t, H, s.ready, {
                        once: !0
                    }), !1 !== xt(t, H) ? this.ready && s.inline && this.view(this.index) : this.ready = !1)
                }
            }]) && o(t.prototype, e), n && o(t, n), i
        }();
    return st(n.prototype, At, r, t, St, Ot), n
});