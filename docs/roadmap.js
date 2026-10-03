/* Netra family sites - live race banner (replaces the old "Roadmap / Coming soon" section).
   Data: race.json on the Netra Eco site. Edit race.json (not this file) as releases ship: set an item state to "shipped" and update "updated".
   Honest rule: only states we set by hand; nothing is guessed. The train time is a tracker estimate and says so. */
(function(){
"use strict";
function mk(tag,css,txt){var e=document.createElement(tag);if(css)e.style.cssText=css;if(txt!=null)e.textContent=txt;return e}
function pad(n){return n<10?"0"+n:""+n}
function run(D){
 var items=D.items||[],done=items.filter(function(i){return i.state==="shipped"}).length;
 var bar=mk("section","background:#0b1220;color:#e8eefc;border-bottom:3px solid #F59E0B;padding:12px 14px;font-family:system-ui,sans-serif;text-align:center");bar.id="race-banner";
 bar.appendChild(mk("div","font-weight:800;font-size:1.05rem",D.title));
 if(D.gift)bar.appendChild(mk("div","font-weight:700;color:#F59E0B;font-size:.9rem;margin:2px 0",D.gift));
 if(D.sub)bar.appendChild(mk("div","font-size:.85rem;color:#9db0d0;margin:2px 0 8px",D.sub));
 var row=mk("div","display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:6px");
 var tr=mk("div","border:1px solid #22314f;border-radius:12px;padding:6px 14px;min-width:150px");
 tr.appendChild(mk("div","font-size:.78rem;color:#9db0d0",D.trainName+" (Jaipur)"));var cd=mk("div","font-weight:800;font-variant-numeric:tabular-nums");tr.appendChild(cd);row.appendChild(tr);
 var ap=mk("div","border:1px solid #F59E0B;border-radius:12px;padding:6px 14px;min-width:150px");
 ap.appendChild(mk("div","font-size:.78rem;color:#9db0d0","Netra releases shipped"));ap.appendChild(mk("div","font-weight:800",done+" of "+items.length));row.appendChild(ap);
 bar.appendChild(row);
 var ul=mk("div","font-size:.82rem;line-height:1.5");
 items.forEach(function(i){var s=i.state==="shipped"?"SHIPPED":(i.state==="building"?"BUILDING":"QUEUED");var c=i.state==="shipped"?"#3CE66E":(i.state==="building"?"#FF9800":"#9db0d0");
  var l=mk("div","");l.appendChild(mk("b","color:"+c,s+" "));l.appendChild(document.createTextNode(i.name));ul.appendChild(l)});
 bar.appendChild(ul);
 bar.appendChild(mk("div","font-size:.75rem;color:#9db0d0;margin-top:6px",(D.trainNote||"")+" Updated "+D.updated+"."));
 document.body.insertBefore(bar,document.body.firstChild);
 var t=Date.parse(D.trainArrival);
 function tick(){var ms=t-Date.now();if(isNaN(t)){cd.textContent="Unavailable";return}
  if(ms<=0){cd.textContent="Arrival time reached";return}
  var sc=Math.floor(ms/1000);cd.textContent=pad(Math.floor(sc/3600))+"h "+pad(Math.floor(sc%3600/60))+"m "+pad(sc%60)+"s (est.)"}
 tick();setInterval(tick,1000);
}
fetch("https://prayagi-store-and-services.github.io/netra-eco/race.json",{cache:"no-cache"}).then(function(r){if(!r.ok)throw 0;return r.json()}).then(run).catch(function(){});
})();
