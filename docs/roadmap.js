/* Netra family sites - "Roadmap / Coming soon".
   Websites only (never inside the apps). Every date below is OUR OWN ESTIMATE, not a promise, and is labelled that way on the page.
   Update this list as part of every release: remove an item when it ships (it then appears under "What's new"), and change an ETA when the estimate changes.
   apps: bspn, kbc, netra-hub, prayagi-privacy. The mother site (Netra Eco) shows one dedicated section per app. */
(function(){
"use strict";
var REVIEWED="3 Oct 2026";
var APPS=[
 {id:"bspn",name:"Battery Sentinel Pro Netra (BSPN)"},
 {id:"kbc",name:"KBC (TarkShastra)"},
 {id:"netra-hub",name:"Netra Hub (Netra Human Safety)"},
 {id:"prayagi-privacy",name:"Prayagi Privacy (SensorGuard)"}
];
var ITEMS=[
 {apps:["bspn","kbc","netra-hub","prayagi-privacy"],title:"One voice, part 1: one speaker per topic",text:"Netra apps on the same phone recognise each other (same Netra signature only) and agree on who speaks. Each topic, like battery percent or temperature, is announced by one app only, never twice. All on the phone, offline, never between different phones.",status:"In progress",eta:"2026-10-06T20:00:00+05:30"},
 {apps:["bspn","kbc","netra-hub","prayagi-privacy"],title:"One voice, part 2: apps can ask each other for status",text:"An app can ask another Netra app on the same phone for information it lacks, such as battery or temperature state. Still offline and on the same phone only.",status:"Next portion",eta:"2026-10-09T20:00:00+05:30"},
 {apps:["kbc"],title:"Battery and temperature check before a game",text:"Before a KBC game starts it checks battery and temperature with the other Netra apps on the phone. If the phone is too hot the game can pause so it cools down. If no answer comes in 2 seconds, the game is not blocked.",status:"Planned",eta:"2026-10-10T20:00:00+05:30"},
 {apps:["bspn","kbc","netra-hub","prayagi-privacy"],title:"Festival open animations",text:"When the app opens on a festival or national day, a short skippable animation for that occasion plays (for example the national flag on Independence Day) instead of a plain banner. Works offline.",status:"In progress",eta:"2026-10-12T20:00:00+05:30"},
 {apps:["prayagi-privacy"],title:"Mic and camera switched off when the screen turns off, and where each app's data goes",text:"SensorGuard will cut microphone and camera access when the screen turns off, and will show per app where its data is going.",status:"Planned",eta:"2026-10-25T20:00:00+05:30"},
 {apps:["kbc"],title:"No repeated questions and camera indicator for fair play",text:"KBC will not repeat a question you already saw, and will show a clear camera-in-use indicator for the anti-cheat check.",status:"Planned",eta:"2026-10-25T20:00:00+05:30"},
 {apps:["bspn"],title:"Solar and inverter monitoring",text:"Solar generation, grid and battery status of your home system inside Battery Sentinel Pro Netra. The detailed spec is still being decided with the owner, so this estimate is rough.",status:"Planning (spec pending)",eta:"2026-12-15T20:00:00+05:30"},
 {apps:["netra-hub"],title:"Disaster alerts, part 1: weather orange alerts (India)",text:"Orange-or-higher weather alerts from IMD for your area, relayed only from the official source. The website checks the alert and pushes it only to people in the affected area, so the phone does not poll and battery is saved.",status:"First portion, being prioritised",eta:"2026-10-08T20:00:00+05:30"},
 {apps:["netra-hub"],title:"Disaster alerts, part 2: earthquakes within about 200 km",text:"Earthquake alerts from official feeds only (USGS, NDMA and equivalent agencies). We relay official warnings. We cannot predict earthquakes.",status:"Second portion",eta:"2026-10-11T20:00:00+05:30"},
 {apps:["netra-hub"],title:"Disaster alerts, part 3: full suite",text:"More hazards, other countries and states using each country's own government agency, and a public alert report (area name and number of people alerted, never anyone's location).",status:"Third portion",eta:"2026-11-30T20:00:00+05:30"}
];
var s=window.NETRA_SITE||{};
var cur=window.NETRA_ROADMAP_APP||s.appId||"eco";
var foot=document.querySelector("footer");if(!foot)return;
var cds=[];
function mk(tag,css,txt){var e=document.createElement(tag);if(css)e.style.cssText=css;if(txt!=null)e.textContent=txt;return e}
function nameOf(id){for(var i=0;i<APPS.length;i++)if(APPS[i].id===id)return APPS[i].name;return id}
function entry(it,appName){
 var d=mk("div","border-top:1px solid var(--line,#22314f);padding:10px 0");
 var tg=mk("div","display:inline-block;font-size:.78rem;font-weight:700;border:1px solid var(--accent,#F59E0B);color:var(--accent,#F59E0B);border-radius:999px;padding:1px 10px;margin-bottom:4px","Coming to: "+appName);d.appendChild(tg);
 var t=mk("div","",null);t.appendChild(mk("b","",it.title));t.appendChild(mk("span","color:var(--accent,#F59E0B);font-size:.85rem"," - "+it.status));d.appendChild(t);
 d.appendChild(mk("div","margin:4px 0;font-size:.92rem",it.text));
 var when;try{when=new Date(it.eta).toLocaleDateString(undefined,{day:"numeric",month:"short",year:"numeric"})}catch(_){when=it.eta}
 d.appendChild(mk("div","font-size:.85rem;color:var(--mute,#9db0d0)","Estimated release: "+when+" (our estimate, can change)"));
 var c=mk("div","font-variant-numeric:tabular-nums;font-weight:700;margin-top:2px");d.appendChild(c);cds.push([c,Date.parse(it.eta)]);
 return d;
}
var sec=mk("section","background:var(--card,#121c30);border:1px solid var(--line,#22314f);border-radius:16px;padding:18px;margin:16px auto;max-width:860px;color:var(--text,#e8eefc)");sec.id="roadmap";
sec.appendChild(mk("h2","margin:0 0 4px;font-size:1.15rem","Roadmap / Coming soon"));
sec.appendChild(mk("p","margin:0 0 10px;color:var(--mute,#9db0d0);font-size:.85rem","What each app is getting next. Release dates are our own estimates, not promises, and can move. Reviewed "+REVIEWED+"."));
function byEta(a,b){return Date.parse(a.eta)-Date.parse(b.eta)}
if(cur==="eco"){
 APPS.forEach(function(a){
  var mine=ITEMS.filter(function(i){return i.apps.indexOf(a.id)>=0}).sort(byEta);
  var box=mk("div","margin-top:14px");box.appendChild(mk("h3","margin:0 0 2px;font-size:1.05rem",a.name));
  if(!mine.length)box.appendChild(mk("div","color:var(--mute,#9db0d0);font-size:.9rem","Nothing announced yet."));
  mine.forEach(function(i){box.appendChild(entry(i,a.name))});
  sec.appendChild(box);
 });
}else{
 var mine=ITEMS.filter(function(i){return i.apps.indexOf(cur)>=0}).sort(byEta);
 if(!mine.length)return;
 mine.forEach(function(i){sec.appendChild(entry(i,nameOf(cur)))});
}
foot.parentNode.insertBefore(sec,foot);
function pad(n){return n<10?"0"+n:""+n}
function tick(){var now=Date.now();
 cds.forEach(function(a){var ms=a[1]-now;
  if(ms<=0){a[0].textContent="Estimate passed - still being finished. We will post a new estimate.";return}
  var sc=Math.floor(ms/1000);a[0].textContent="Estimated in "+Math.floor(sc/86400)+"d "+pad(Math.floor(sc%86400/3600))+"h "+pad(Math.floor(sc%3600/60))+"m "+pad(sc%60)+"s";
 });
}
tick();setInterval(tick,1000);
})();
