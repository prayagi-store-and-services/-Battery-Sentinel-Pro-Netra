/* Netra family sites - "Roadmap / Coming soon".
   Websites only (never inside the apps). Every date below is OUR OWN ESTIMATE, not a promise, and is labelled that way on the page.
   Update this list as part of every release: remove an item when it ships (it then appears under "What's new"), and change an ETA when the estimate changes.
   Format: {id, apps:["bspn","kbc","netra-hub","prayagi-privacy","eco"], title, text, status, eta:"YYYY-MM-DDTHH:MM:SS+05:30"} */
(function(){
"use strict";
var REVIEWED="3 Oct 2026";
var ITEMS=[
 {id:"crash",apps:["bspn","kbc","netra-hub","prayagi-privacy","eco"],title:"Automatic crash reports",text:"If an app crashes, the next time it opens it sends a short report by itself (app, phone model, Android and app version, code locations only). No personal data. The manual send button goes away.",status:"Rolling out now",eta:"2026-10-03T18:00:00+05:30"},
 {id:"voice",apps:["bspn","kbc","netra-hub","prayagi-privacy","eco"],title:"One voice for all Netra apps",text:"Netra apps on the same phone recognise each other (same Netra signature only) and agree on who speaks. Each topic, like battery percent or temperature, is announced by one app only, never twice. Apps can also ask each other for status. All on the phone, offline, never between different phones.",status:"In progress",eta:"2026-10-08T20:00:00+05:30"},
 {id:"gate",apps:["kbc","bspn","netra-hub","eco"],title:"KBC game battery and temperature check",text:"Before a KBC game starts it checks battery and temperature with the other Netra apps on the phone. If the phone is too hot the game can pause so it cools down. If no answer comes in 2 seconds, the game is not blocked.",status:"Planned",eta:"2026-10-10T20:00:00+05:30"},
 {id:"fest",apps:["bspn","kbc","netra-hub","prayagi-privacy","eco"],title:"Festival open animations",text:"When an app opens on a festival or national day, a short skippable animation for that occasion plays (for example the national flag on Independence Day) instead of a plain banner. Works offline. Websites get the same themed visuals.",status:"In progress",eta:"2026-10-12T20:00:00+05:30"},
 {id:"disaster",apps:["netra-hub","eco"],title:"Disaster alerts in Netra Human Safety",text:"Earthquake alerts within about 200 km and orange-or-higher weather alerts for your area, only from official sources (USGS, IMD, NDMA and equivalent agencies). We relay official warnings. We cannot predict earthquakes. The website checks the official source and pushes alerts only to people in the affected area, so the phone does not poll and battery is saved.",status:"Design stage",eta:"2026-11-30T20:00:00+05:30"}
];
var s=window.NETRA_SITE||{};
var app=window.NETRA_ROADMAP_APP||s.appId||"eco";
var list=ITEMS.filter(function(i){return i.apps.indexOf(app)>=0}).sort(function(a,b){return Date.parse(a.eta)-Date.parse(b.eta)});
if(!list.length)return;
var foot=document.querySelector("footer");if(!foot)return;
var sec=document.createElement("section");sec.id="roadmap";
sec.style.cssText="background:var(--card,#121c30);border:1px solid var(--line,#22314f);border-radius:16px;padding:18px;margin:16px auto;max-width:860px;color:var(--text,#e8eefc)";
var h=document.createElement("h2");h.textContent="Roadmap / Coming soon";h.style.cssText="margin:0 0 4px;font-size:1.15rem";sec.appendChild(h);
var p=document.createElement("p");p.style.cssText="margin:0 0 10px;color:var(--mute,#9db0d0);font-size:.85rem";
p.textContent="What we are working on. Release dates are our own estimates, not promises, and can move. Reviewed "+REVIEWED+".";sec.appendChild(p);
var cds=[];
list.forEach(function(i){
 var d=document.createElement("div");d.style.cssText="border-top:1px solid var(--line,#22314f);padding:10px 0";
 var t=document.createElement("b");t.textContent=i.title;d.appendChild(t);
 var st=document.createElement("span");st.textContent=" - "+i.status;st.style.cssText="color:var(--accent,#F59E0B);font-size:.85rem";d.appendChild(st);
 var x=document.createElement("div");x.textContent=i.text;x.style.cssText="margin:4px 0;font-size:.92rem";d.appendChild(x);
 var e=document.createElement("div");e.style.cssText="font-size:.85rem;color:var(--mute,#9db0d0)";
 var when;try{when=new Date(i.eta).toLocaleDateString(undefined,{day:"numeric",month:"short",year:"numeric"})}catch(_){when=i.eta}
 e.textContent="Estimated release: "+when+" (our estimate)";d.appendChild(e);
 var c=document.createElement("div");c.style.cssText="font-variant-numeric:tabular-nums;font-weight:700;margin-top:2px";c.setAttribute("aria-live","off");d.appendChild(c);
 cds.push([c,Date.parse(i.eta)]);sec.appendChild(d);
});
foot.parentNode.insertBefore(sec,foot);
function pad(n){return n<10?"0"+n:""+n}
function tick(){
 var now=Date.now();
 cds.forEach(function(a){var ms=a[1]-now;
  if(ms<=0){a[0].textContent="Estimate passed - still being finished. We will post a new estimate.";return}
  var sc=Math.floor(ms/1000),dd=Math.floor(sc/86400),hh=Math.floor(sc%86400/3600),mm=Math.floor(sc%3600/60),ss=sc%60;
  a[0].textContent="Estimated in "+dd+"d "+pad(hh)+"h "+pad(mm)+"m "+pad(ss)+"s";
 });
}
tick();setInterval(tick,1000);
})();
