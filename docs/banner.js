/* Battery Sentinel Pro Netra - dynamic banner, theme, live clock and "What's new".
   Honest data rules: festival dates are for India (source: timeanddate.com India calendar, 2026 and 2027);
   Independence Day: India only (15 August); other countries' independence days were removed on 4 Oct 2026.
   No death anniversaries, ever. After 2027 there is no festival data, so no festival banner is shown (nothing is invented).
   See docs/BANNER.md for how to add a condolence banner by hand. */
(function(){
"use strict";
/* ---- CONDOLENCE BANNERS: edit this array by hand when news breaks (see docs/BANNER.md) ----
   Example (do not leave examples uncommented):
   {from:"2026-10-05", to:"2026-10-08", name:"Full name", country:"Country", text:"Short respectful line"} */
var CONDOLENCES=[];

var FEST=[[20260103,"Hazarat Ali's Birthday",0],[20260113,"Lohri",0],[20260114,"Makar Sankranti",0],[20260114,"Pongal",0],[20260123,"Vasant Panchami",0],[20260126,"Republic Day",0],[20260201,"Guru Ravidas Jayanti",0],[20260212,"Maharishi Dayanand Saraswati Jayanti",0],[20260215,"Maha Shivaratri",0],[20260217,"Lunar New Year",0],[20260219,"Ramadan Start",0],[20260219,"Shivaji Jayanti",0],[20260303,"Holika Dahana",0],[20260304,"Holi",0],[20260319,"Gudi Padwa",0],[20260319,"Ugadi",0],[20260320,"Jamat Ul-Vida",0],[20260321,"Ramzan Id",0],[20260326,"Rama Navami",0],[20260331,"Mahavir Jayanti",0],[20260402,"First day of Passover",0],[20260402,"Maundy Thursday",0],[20260403,"Good Friday",0],[20260405,"Easter Day",0],[20260414,"Ambedkar Jayanti",0],[20260414,"Mesadi",0],[20260414,"Vaisakhi",0],[20260415,"Bahag Bihu",0],[20260501,"Buddha Purnima",0],[20260501,"International Worker's Day",0],[20260509,"Birthday of Rabindranath",0],[20260528,"Bakrid",0],[20260716,"Rath Yatra",0],[20260729,"Guru Purnima",0],[20260802,"Friendship Day",0],[20260826,"Milad un-Nabi",0],[20260826,"Onam",0],[20260828,"Raksha Bandhan",0],[20260904,"Janmashtami",0],[20260904,"Janmashtami (Smarta)",0],[20260914,"Ganesh Chaturthi",0],[20261002,"Mahatma Gandhi Jayanti",0],[20261011,"First Day of Sharad Navratri",0],[20261017,"First Day of Durga Puja Festivities",0],[20261018,"Maha Saptami",0],[20261019,"Maha Ashtami",0],[20261020,"Dussehra",0],[20261020,"Maha Navami",1],[20261021,"Durga Puja ends (Vijaya Dashami)",1],[20261026,"Maharishi Valmiki Jayanti",0],[20261029,"Karaka Chaturthi",0],[20261108,"Diwali/Deepavali",0],[20261108,"Naraka Chaturdasi",0],[20261109,"Govardhan Puja",0],[20261111,"Bhai Duj",0],[20261115,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20261124,"Guru Nanak Jayanti",0],[20261205,"First Day of Hanukkah",0],[20261212,"Last day of Hanukkah",0],[20261223,"Hazarat Ali's Birthday",0],[20261224,"Christmas Eve",0],[20261225,"Christmas",0],[20261231,"New Year's Eve",0],[20270101,"New Year's Day",0],[20270114,"Makar Sankranti",0],[20270115,"Guru Govind Singh Jayanti",0],[20270115,"Pongal",0],[20270126,"Republic Day",0],[20270206,"Lunar New Year",0],[20270209,"Ramadan Start",1],[20270211,"Vasant Panchami",0],[20270219,"Shivaji Jayanti",0],[20270220,"Guru Ravidas Jayanti",0],[20270302,"Maharishi Dayanand Saraswati Jayanti",0],[20270305,"Jamat Ul-Vida",0],[20270306,"Maha Shivaratri",0],[20270310,"Ramzan Id",1],[20270322,"Dolyatra",0],[20270322,"Holi",0],[20270322,"Holika Dahana",0],[20270325,"Maundy Thursday",0],[20270326,"Good Friday",0],[20270328,"Easter Day",0],[20270407,"Chaitra Sukhladi",0],[20270407,"Cheti Chand",0],[20270407,"Gudi Padwa",0],[20270407,"Ugadi",0],[20270414,"Ambedkar Jayanti",0],[20270414,"Mesadi",0],[20270414,"Vaisakhi",0],[20270414,"Vishu",0],[20270415,"Bahag Bihu (Assam)",0],[20270415,"Rama Navami",0],[20270415,"Vaisakhadi (Bengal)",0],[20270419,"Mahavir Jayanti",0],[20270422,"First day of Passover",0],[20270501,"International Worker's Day",0],[20270509,"Birthday of Rabindranath",0],[20270517,"Bakrid",1],[20270520,"Buddha Purnima",0],[20270705,"Rath Yatra",0],[20270801,"Friendship Day",0],[20270815,"Milad un-Nabi",1],[20270815,"Parsi New Year",0],[20270817,"Raksha Bandhan",0],[20270825,"Janmashtami",0],[20270825,"Janmashtami (Smarta)",0],[20270904,"Ganesh Chaturthi",0],[20270912,"Onam",0],[20270930,"First Day of Sharad Navratri",0],[20271002,"Mahatma Gandhi Jayanti",0],[20271005,"First Day of Durga Puja Festivities",0],[20271006,"Maha Saptami",0],[20271007,"Maha Ashtami",0],[20271008,"Maha Navami",0],[20271009,"Dussehra",0],[20271015,"Maharishi Valmiki Jayanti",0],[20271018,"Karaka Chaturthi",0],[20271028,"Naraka Chaturdasi",0],[20271029,"Diwali/Deepavali",0],[20271030,"Govardhan Puja",0],[20271031,"Bhai Duj",0],[20271104,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20271114,"Guru Nanak Jayanti",0],[20271212,"Hazarat Ali's Birthday",0],[20271224,"Christmas Eve",0],[20271225,"Christmas",0],[20271225,"First Day of Hanukkah",0],[20271231,"New Year's Eve",0]]; /* [yyyymmdd, name, dateMayShiftByADay] */
var INDEP=[["India","Independence Day",8,15]]; /* [country, event, month, day] */

var THEMES={
 default:null,
 holi:{accent:"#f472b6",accent2:"#facc15",bg:"#1a0b1e",card:"#26112b",card2:"#311a38",line:"#4d2a5a"},
 diwali:{accent:"#fbbf24",accent2:"#fb923c",bg:"#1a1005",card:"#261a0a",card2:"#32230f",line:"#54391a"},
 eid:{accent:"#34d399",accent2:"#a7f3d0",bg:"#07170f",card:"#0d2418",card2:"#133121",line:"#1f5238"},
 xmas:{accent:"#f87171",accent2:"#4ade80",bg:"#150a0a",card:"#221111",card2:"#2e1818",line:"#4d2626"},
 saffron:{accent:"#fb923c",accent2:"#fbbf24",bg:"#190e06",card:"#25160a",card2:"#311e0f",line:"#523219"},
 india:{accent:"#ff9933",accent2:"#22c55e",bg:"#0b1220",card:"#121c30",card2:"#17233b",line:"#22314f"},
 fest:{accent:"#a78bfa",accent2:"#f472b6",bg:"#100b1f",card:"#181230",card2:"#201840",line:"#352a63"},
 sorrow:{accent:"#cbd5e1",accent2:"#94a3b8",bg:"#09090b",card:"#121214",card2:"#18181b",line:"#2b2b30",text:"#e4e4e7",mute:"#a1a1aa"}
};
function themeFor(n){
 if(/Holi|Dolyatra/i.test(n))return"holi";
 if(/Diwali|Deepavali|Govardhan|Bhai Duj|Naraka/i.test(n))return"diwali";
 if(/Ramzan|Ramadan|Bakrid|Milad|Jamat|Id\b/i.test(n))return"eid";
 if(/Christmas|Easter|Good Friday|Maundy/i.test(n))return"xmas";
 if(/Buddha|Mahavir|Navratri|Durga|Dussehra|Ganesh|Janmashtami|Rama|Shivaratri|Rath|Raksha|Onam|Pongal|Sankranti|Lohri|Ugadi|Gudi|Vaisakhi|Guru|Navami|Ashtami|Saptami|Chhat|Karaka/i.test(n))return"saffron";
 if(/Republic Day/i.test(n))return"india";
 return"fest";
}
function applyTheme(key){
 var t=THEMES[key];if(!t)return;var s=document.documentElement.style;
 s.setProperty("--accent",t.accent);s.setProperty("--accent2",t.accent2);
 (key==="sorrow"?["bg","card","card2","line","text","mute"]:[]).forEach(function(k){if(t[k])s.setProperty("--"+k,t[k])});
}
function ymd(d){return d.getFullYear()*10000+(d.getMonth()+1)*100+d.getDate()}
function pick(now){
 var today=ymd(now),i;
 for(i=0;i<CONDOLENCES.length;i++){var c=CONDOLENCES[i];
  var f=Number(String(c.from).replace(/-/g,"")),t=Number(String(c.to).replace(/-/g,""));
  if(today>=f&&today<=t)return{kind:"sorrow",theme:"sorrow",title:"Shok sandesh",text:(c.name?c.name+(c.country?" ("+c.country+")":"")+" ke nidhan par shok. ":"")+(c.text||"")}}
 var m=now.getMonth()+1,d=now.getDate(),names=[],ind=[],th=null;
 FEST.forEach(function(f){if(f[0]===today){names.push(f[1]+(f[2]?" (date may differ by a day)":""));if(!th)th=themeFor(f[1])}});
 var indiaInd=false;
 INDEP.forEach(function(x){if(x[2]===m&&x[3]===d){ind.push(x[0]);if(x[0]==="India")indiaInd=true}});
 if(names.length||ind.length){
  var parts=[];
  if(names.length)parts.push("Aaj: "+names.join(", "));
  if(ind.length)parts.push("Independence Day: "+ind.join(", "));
  return{kind:"fest",theme:indiaInd?"india":th,title:"Shubhkamnayein",text:parts.join(" - ")}}
 var best=null;
 for(var k=1;k<=3&&!best;k++){var n=new Date(now.getFullYear(),now.getMonth(),now.getDate()+k),y=ymd(n),nm=[];
  FEST.forEach(function(f){if(f[0]===y)nm.push(f[1])});
  if(nm.length)best={kind:"soon",theme:themeFor(nm[0]),title:"Aane wala",text:nm.join(", ")+(k===1?" - kal":" - "+k+" din baad")}}
 return best;
}
function render(){
 var el=document.getElementById("festbar"),p=pick(new Date());
 if(!el)return;
 if(!p){el.hidden=true;return}
 applyTheme(p.theme);
 el.className="festbar "+p.kind;el.hidden=false;if(p.kind!=="sorrow"){el.style.background="transparent";el.style.border="0";el.style.color="var(--text,inherit)"}
 el.innerHTML="";var b=document.createElement("b");b.textContent=p.title+": ";el.appendChild(b);el.appendChild(document.createTextNode(p.text));
}
function clock(){
 var el=document.getElementById("liveclock");if(!el)return;
 function t(){var d=new Date();el.textContent=d.toLocaleDateString(undefined,{weekday:"long",day:"numeric",month:"long",year:"numeric"})+" - "+d.toLocaleTimeString()}
 t();setInterval(t,1000);
}
function whatsNew(){
 var box=document.getElementById("whatsnew-list");if(!box)return;
 var repo="prayagi-store-and-services/-Battery-Sentinel-Pro-Netra";
 fetch("https://api.github.com/repos/"+repo+"/releases?per_page=5").then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(rel){
  rel=rel.filter(function(x){return !x.draft&&!x.prerelease});box.innerHTML="";
  if(!rel.length){box.textContent="No releases yet.";return}
  rel.slice(0,3).forEach(function(x){
   var h=document.createElement("h3"),a=document.createElement("a");a.href=x.html_url;a.textContent=x.tag_name;h.appendChild(a);
   var dt=document.createElement("span");dt.className="sub";try{dt.textContent="  "+new Date(x.published_at).toLocaleDateString(undefined,{year:"numeric",month:"short",day:"numeric"})}catch(e){}h.appendChild(dt);box.appendChild(h);
   var ul=document.createElement("ul"),n=0;
   String(x.body||"").split(/\r?\n/).forEach(function(l){
    var m=l.match(/^\s*[-*]\s+(.*)$/);if(m&&n<8){var li=document.createElement("li");li.textContent=m[1].replace(/\*\*/g,"");ul.appendChild(li);n++}});
   if(!n){var li=document.createElement("li");li.textContent="See the release page for the notes.";ul.appendChild(li)}
   box.appendChild(ul)});
 }).catch(function(){box.innerHTML="";var a=document.createElement("a");a.href="https://github.com/"+repo+"/releases";a.textContent="Could not load. Open the release notes on GitHub.";box.appendChild(a)});
}
render();clock();whatsNew();
setInterval(render,600000);
})();

/* Navratri look, 11-20 Oct 2026 (device date, no network). Red, orange and gold accents plus a short festive strip. */
(function(){
"use strict";
try{
var d=new Date(),k=d.getFullYear()*10000+(d.getMonth()+1)*100+d.getDate();
if(k<20261011||k>20261020)return;
var st=document.createElement("style");
st.textContent=":root{--accent:#F2C14E!important;--accent2:#FF8A00!important;--line:#6b2a1c!important;--ds-teal:#B71C1C!important}"+
 "#navratri-strip{background:linear-gradient(90deg,#B71C1C,#E65100,#F2C14E,#E65100,#B71C1C);color:#fff;text-align:center;font:600 14px/34px system-ui,sans-serif;letter-spacing:.04em;text-shadow:0 1px 2px rgba(0,0,0,.45)}";
document.head.appendChild(st);
var s=document.createElement("div");s.id="navratri-strip";s.textContent="Jay Mata Di  |  Shubh Navratri";
s.setAttribute("role","note");
(document.body||document.documentElement).insertBefore(s,(document.body||document.documentElement).firstChild);
}catch(e){}
})();

window.NETRA_SITE=window.NETRA_SITE||{};window.NETRA_SITE.appId="battery-sentinel";
/* Official links only. No social SDK, tracking pixel, automatic follow or rating. */
(function(){
'use strict';
var ROOT='https://prayagi-store-and-services.github.io/netra-eco/';
var SOCIAL=[['YouTube','Subscribe','https://www.youtube.com/@NetraEcosystem','▶'],['Instagram','Follow','https://www.instagram.com/netraecosystem/','◎'],['Facebook','Follow','https://www.facebook.com/netraecosystem','f'],['X','Follow','https://x.com/netraecosystem','𝕏'],['Telegram','Join channel','https://t.me/Netraecosystem','➤']];
function el(t,text){var n=document.createElement(t);if(text)n.textContent=text;return n}
function link(text,url){var a=el('a',text);a.href=url;a.rel='noopener noreferrer';return a}
function mount(){
 if(document.getElementById('netra-community'))return;
 var host=document.querySelector('main');if(!host)return;
 var s=el('section');s.id='netra-community';s.setAttribute('aria-label','Netra community and support');
 var st=el('style');st.textContent='#netra-community{margin:28px 0;padding:20px;border:1px solid var(--line,#31415d);border-radius:18px;background:var(--card,#111c2e);color:var(--text,#e8eefc);overflow-wrap:anywhere}#netra-community h2{font-size:1.5rem;margin:0 0 8px}#netra-community .netra-socials{position:static;min-height:0;padding:0;background:none;border:0;display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px;margin:16px 0}#netra-community a{color:var(--accent,#4fd1a5)}#netra-community .netra-socials a{display:flex;flex-direction:column;gap:4px;padding:14px;border:1px solid var(--line,#31415d);border-radius:12px;text-decoration:none;min-height:88px}#netra-community .netra-socials a:focus-visible{outline:3px solid var(--accent,#4fd1a5);outline-offset:3px}#netra-community .netra-social-icon{font-size:1.5rem;font-weight:700}#netra-community p{line-height:1.6}#netra-community iframe{width:100%;min-height:1120px;border:0}';document.head.appendChild(st);
 s.appendChild(el('h2','Connect with Netra Ecosystem'));
 s.appendChild(el('p','Choose a platform to follow, subscribe or join. These links open our official profiles. Nothing is followed, liked or posted automatically.'));
 var grid=el('div');grid.className='netra-socials';grid.setAttribute('role','navigation');grid.setAttribute('aria-label','Official social profiles');
 SOCIAL.forEach(function(x){var a=link('',x[2]),icon=el('span',x[3]);icon.className='netra-social-icon';icon.setAttribute('aria-hidden','true');a.append(icon,el('strong',x[0]),el('span',x[1]));a.setAttribute('aria-label',x[1]+' on '+x[0]);grid.appendChild(a)});s.appendChild(grid);
 var config=window.NETRA_SITE||{},app=document.getElementById('app'),code=app&&app.getAttribute('data-code');
 var ids={bspn:'battery-sentinel',kbc:'kbc',hub:'netra-hub',privacy:'prayagi-privacy',trikaal:'netra-trikaal',player:'netra-player',eco:'netra-eco',charge:'netra-charge'};
 var id=config.appId||ids[code]||'netra-eco';
 var feedback=el('p');feedback.appendChild(link('Rate this app, read reviews or send feedback',ROOT+'embed.html?app='+encodeURIComponent(id)));s.appendChild(feedback);
 s.appendChild(el('p','Honest ratings are welcome, positive or critical. No reward, forced follow or five-star request. Reviews are community submissions, not verified purchases.'));
 s.appendChild(el('h2','Contact us privately'));
 s.appendChild(el('p','For a complaint or feedback you do not want to publish, use this private contact form. Only what you enter is sent after you agree.'));
 if(app){s.appendChild(link('Open private contact form',ROOT+'contact.html?app='+encodeURIComponent(id)));host.appendChild(s);return;}
 s.appendChild(link('Open private contact form in its own page',ROOT+'contact.html?app='+encodeURIComponent(id)));
 var f=el('iframe');f.title='Private Netra contact form';f.loading='lazy';f.src=ROOT+'contact.html?app='+encodeURIComponent(id);s.appendChild(f);
 host.appendChild(s);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount);else mount();
})();
