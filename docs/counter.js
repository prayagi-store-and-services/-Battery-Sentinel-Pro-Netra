/* Anonymous counters on our own Firestore project (netra-ai-jan, collection netra_active, field count).
   Same collection and rules the apps already use: create with 1, or add exactly 1. No cookies, no personal data.
   get(key) gives a number, or null if it cannot be read (nothing is ever made up). hit(key) adds 1 and never blocks the page. */
(function () {
  "use strict";
  var DB = "projects/netra-ai-jan/databases/(default)/documents";
  var FS = "https://firestore.googleapis.com/v1/" + DB;
  function id(k) { k = String(k).toLowerCase(); return /^[a-z0-9-]{1,40}$/.test(k) ? k + "_000000" : null; }
  window.NetraCounter = {
    get: function (k) {
      var i = id(k); if (!i) return Promise.resolve(null);
      return fetch(FS + "/netra_active/" + i).then(function (r) {
        if (r.status === 404) return 0;
        if (!r.ok) return null;
        return r.json().then(function (d) {
          var f = d && d.fields && d.fields.count;
          return f && f.integerValue != null ? parseInt(f.integerValue, 10) : 0;
        });
      }).catch(function () { return null; });
    },
    hit: function (k) {
      var i = id(k); if (!i) return Promise.resolve(false);
      var body = { writes: [{ transform: { document: DB + "/netra_active/" + i, fieldTransforms: [{ fieldPath: "count", increment: { integerValue: "1" } }] } }] };
      return fetch(FS + ":commit", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body), keepalive: true })
        .then(function (r) { return r.ok; }).catch(function () { return false; });
    }
  };
})();
