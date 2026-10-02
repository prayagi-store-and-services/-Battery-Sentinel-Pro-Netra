/* Battery Sentinel Pro Netra - dynamic banner, theme, live clock and "What's new".
   Honest data rules: festival dates are for India (source: timeanddate.com India calendar, 2026 and 2027);
   independence days come from Wikipedia's "List of national independence days" (fixed month/day only).
   No death anniversaries, ever. After 2027 there is no festival data, so no festival banner is shown (nothing is invented).
   See docs/BANNER.md for how to add a condolence banner by hand. */
(function(){
"use strict";
/* ---- CONDOLENCE BANNERS: edit this array by hand when news breaks (see docs/BANNER.md) ----
   Example (do not leave examples uncommented):
   {from:"2026-10-05", to:"2026-10-08", name:"Full name", country:"Country", text:"Short respectful line"} */
var CONDOLENCES=[];

var FEST=[[20260103,"Hazarat Ali's Birthday",0],[20260113,"Lohri",0],[20260114,"Makar Sankranti",0],[20260114,"Pongal",0],[20260123,"Vasant Panchami",0],[20260126,"Republic Day",0],[20260201,"Guru Ravidas Jayanti",0],[20260212,"Maharishi Dayanand Saraswati Jayanti",0],[20260215,"Maha Shivaratri",0],[20260217,"Lunar New Year",0],[20260219,"Ramadan Start",0],[20260219,"Shivaji Jayanti",0],[20260303,"Holika Dahana",0],[20260304,"Holi",0],[20260319,"Gudi Padwa",0],[20260319,"Ugadi",0],[20260320,"Jamat Ul-Vida",0],[20260321,"Ramzan Id",0],[20260326,"Rama Navami",0],[20260331,"Mahavir Jayanti",0],[20260402,"First day of Passover",0],[20260402,"Maundy Thursday",0],[20260403,"Good Friday",0],[20260405,"Easter Day",0],[20260414,"Ambedkar Jayanti",0],[20260414,"Mesadi",0],[20260414,"Vaisakhi",0],[20260415,"Bahag Bihu",0],[20260501,"Buddha Purnima",0],[20260501,"International Worker's Day",0],[20260509,"Birthday of Rabindranath",0],[20260528,"Bakrid",0],[20260716,"Rath Yatra",0],[20260729,"Guru Purnima",0],[20260802,"Friendship Day",0],[20260826,"Milad un-Nabi",0],[20260826,"Onam",0],[20260828,"Raksha Bandhan",0],[20260904,"Janmashtami",0],[20260904,"Janmashtami (Smarta)",0],[20260914,"Ganesh Chaturthi",0],[20261002,"Mahatma Gandhi Jayanti",0],[20261011,"First Day of Sharad Navratri",0],[20261017,"First Day of Durga Puja Festivities",0],[20261018,"Maha Saptami",0],[20261019,"Maha Ashtami",0],[20261020,"Dussehra",0],[20261026,"Maharishi Valmiki Jayanti",0],[20261029,"Karaka Chaturthi",0],[20261108,"Diwali/Deepavali",0],[20261108,"Naraka Chaturdasi",0],[20261109,"Govardhan Puja",0],[20261111,"Bhai Duj",0],[20261115,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20261124,"Guru Nanak Jayanti",0],[20261205,"First Day of Hanukkah",0],[20261212,"Last day of Hanukkah",0],[20261223,"Hazarat Ali's Birthday",0],[20261224,"Christmas Eve",0],[20261225,"Christmas",0],[20261231,"New Year's Eve",0],[20270101,"New Year's Day",0],[20270114,"Makar Sankranti",0],[20270115,"Guru Govind Singh Jayanti",0],[20270115,"Pongal",0],[20270126,"Republic Day",0],[20270206,"Lunar New Year",0],[20270209,"Ramadan Start",1],[20270211,"Vasant Panchami",0],[20270219,"Shivaji Jayanti",0],[20270220,"Guru Ravidas Jayanti",0],[20270302,"Maharishi Dayanand Saraswati Jayanti",0],[20270305,"Jamat Ul-Vida",0],[20270306,"Maha Shivaratri",0],[20270310,"Ramzan Id",1],[20270322,"Dolyatra",0],[20270322,"Holi",0],[20270322,"Holika Dahana",0],[20270325,"Maundy Thursday",0],[20270326,"Good Friday",0],[20270328,"Easter Day",0],[20270407,"Chaitra Sukhladi",0],[20270407,"Cheti Chand",0],[20270407,"Gudi Padwa",0],[20270407,"Ugadi",0],[20270414,"Ambedkar Jayanti",0],[20270414,"Mesadi",0],[20270414,"Vaisakhi",0],[20270414,"Vishu",0],[20270415,"Bahag Bihu (Assam)",0],[20270415,"Rama Navami",0],[20270415,"Vaisakhadi (Bengal)",0],[20270419,"Mahavir Jayanti",0],[20270422,"First day of Passover",0],[20270501,"International Worker's Day",0],[20270509,"Birthday of Rabindranath",0],[20270517,"Bakrid",1],[20270520,"Buddha Purnima",0],[20270705,"Rath Yatra",0],[20270801,"Friendship Day",0],[20270815,"Milad un-Nabi",1],[20270815,"Parsi New Year",0],[20270817,"Raksha Bandhan",0],[20270825,"Janmashtami",0],[20270825,"Janmashtami (Smarta)",0],[20270904,"Ganesh Chaturthi",0],[20270912,"Onam",0],[20270930,"First Day of Sharad Navratri",0],[20271002,"Mahatma Gandhi Jayanti",0],[20271005,"First Day of Durga Puja Festivities",0],[20271006,"Maha Saptami",0],[20271007,"Maha Ashtami",0],[20271008,"Maha Navami",0],[20271009,"Dussehra",0],[20271015,"Maharishi Valmiki Jayanti",0],[20271018,"Karaka Chaturthi",0],[20271028,"Naraka Chaturdasi",0],[20271029,"Diwali/Deepavali",0],[20271030,"Govardhan Puja",0],[20271031,"Bhai Duj",0],[20271104,"Chhat Puja (Pratihar Sashthi/Surya Sashthi)",0],[20271114,"Guru Nanak Jayanti",0],[20271212,"Hazarat Ali's Birthday",0],[20271224,"Christmas Eve",0],[20271225,"Christmas",0],[20271225,"First Day of Hanukkah",0],[20271231,"New Year's Eve",0]]; /* [yyyymmdd, name, dateMayShiftByADay] */
var INDEP=[["Haiti","Independence Day",1,1],["Sudan","Independence Day",1,1],["Myanmar","Independence Day",1,4],["Morocco","Proclamation of Independence Day",1,11],["Nauru","Independence Day",1,31],["Sri Lanka","Independence Day",2,4],["Grenada","Independence Day",2,7],["Kosovo","Independence Day",2,17],["The Gambia","Independence Day",2,18],["Saint Lucia","Independence Day",2,22],["Estonia","Independence Day",2,24],["Bosnia and Herzegovina","Independence Day",3,1],["Ghana","Independence Day",3,6],["Lithuania","Day of Restoration of Independence",3,11],["Mauritius","Independence Day",3,12],["Tunisia","Independence Day",3,20],["Namibia","Independence Day",3,21],["Greece","Independence Day",3,25],["Bangladesh","Independence and National Day",3,26],["Senegal","Independence Day",4,4],["Syria","Independence Day",4,17],["Sierra Leone","Independence Day",4,27],["Togo","Independence Day",4,27],["Latvia","Day of Restoration of Independence",5,4],["Romania","National Independence Day",5,10],["Paraguay","Independence Day",5,14],["Timor-Leste","Day of Restoration of Independence",5,20],["Montenegro","Independence Day",5,21],["Eritrea","Independence Day",5,24],["Jordan","Independence Day",5,25],["Georgia","Independence Day",5,26],["Guyana","Independence Day",5,26],["Azerbaijan","Independence Day",5,28],["Samoa","Independence Day",6,1],["Norway","Independence Day",6,7],["Philippines","Independence Day",6,12],["Mozambique","Independence Day",6,25],["Madagascar","Independence Day",6,26],["Somalia","Independence Day",6,26],["Djibouti","Independence Day",6,27],["Seychelles","Independence Day",6,29],["Democratic Republic of the Congo","Independence Day",6,30],["Burundi","Independence Day",7,1],["Rwanda","Independence Day",7,1],["Belarus","Independence Day",7,3],["United States","Independence Day",7,4],["Algeria","Independence Day",7,5],["Cape Verde","Independence Day",7,5],["Venezuela","Independence Day",7,5],["Comoros","Independence Day",7,6],["Malawi","Independence Day",7,6],["Solomon Islands","Independence Day",7,7],["Argentina","Independence Day",7,9],["South Sudan","Independence Day",7,9],["Bahamas","Independence Day",7,10],["São Tomé and Príncipe","Independence Day",7,12],["Slovakia","Independence Day",7,17],["Liberia","Independence Day",7,26],["Maldives","Independence Day",7,26],["Peru","Independence Day",7,28],["Vanuatu","Independence Day",7,30],["Benin","Independence Day",8,1],["Niger","Independence Day",8,3],["Burkina Faso","Independence Day",8,5],["Bolivia","Independence Day",8,6],["Jamaica","Independence Day",8,6],["Ivory Coast","Independence Day",8,7],["Ecuador","Independence Day",8,10],["Chad","Independence Day",8,11],["Central African Republic","Independence Day",8,13],["India","Independence Day",8,15],["Republic of the Congo","Independence Day",8,15],["South Korea","Liberation Day (Independence from Japan)",8,15],["Gabon","Independence Day",8,16],["Indonesia","Independence Day",8,17],["Afghanistan","Independence Day",8,19],["Estonia","Day of Restoration of Independence",8,20],["Ukraine","Independence Day",8,24],["Uruguay","Independence Day",8,25],["Moldova","Independence Day",8,27],["Kyrgyzstan","Independence Day",8,31],["Malaysia","Independence Day/National Day",8,31],["Trinidad and Tobago","Independence Day",8,31],["Uzbekistan","Independence Day",9,1],["Eswatini","Independence Day (Somhlolo Day)",9,6],["Brazil","Independence Day",9,7],["North Macedonia","Independence Day",9,8],["Tajikistan","Independence Day",9,9],["Costa Rica","Independence Day",9,15],["El Salvador","Independence Day",9,15],["Guatemala","Independence Day",9,15],["Honduras","Independence Day",9,15],["Nicaragua","Independence Day",9,15],["Mexico","Independence Day",9,16],["Papua New Guinea","Independence Day",9,16],["Chile","Independence Day",9,18],["Saint Kitts and Nevis","Independence Day",9,19],["Armenia","Independence Day",9,21],["Belize","Independence Day",9,21],["Malta","Independence Day",9,21],["Bulgaria","Independence Day",9,22],["Mali","Independence Day",9,22],["Guinea-Bissau","Independence Day",9,24],["Turkmenistan","Independence Day",9,27],["Botswana","Independence Day",9,30],["Cyprus","Independence Day",10,1],["Nigeria","Independence Day",10,1],["Palau","Independence Day",10,1],["Tuvalu","Independence Day",10,1],["Guinea","Independence Day",10,2],["Iraq","Independence Day",10,3],["Lesotho","Independence Day",10,4],["Uganda","Independence Day",10,9],["Fiji","Fiji Day (Independence)",10,10],["Equatorial Guinea","Independence Day",10,12],["Azerbaijan","Day of Restoration of Independence",10,18],["Zambia","Independence Day",10,24],["Saint Vincent and the Grenadines","Independence Day",10,27],["Czech Republic","Independence Day",10,28],["Slovakia","Independence Day",10,28],["Antigua and Barbuda","Independence Day",11,1],["Dominica","Independence Day",11,3],["Micronesia","Independence Day",11,3],["Cambodia","Independence Day",11,9],["Angola","Independence Day",11,11],["Poland","Independence Day",11,11],["Morocco","Independence Day",11,18],["Lebanon","Independence Day",11,22],["Suriname","Independence Day",11,25],["Albania","Independence Day",11,28],["Mauritania","Independence Day",11,28],["Panama","Independence Day",11,28],["Timor-Leste","Proclamation of Independence Day",11,28],["Barbados","Independence Day",11,30],["Yemen","Independence Day",11,30],["Portugal","Restoration of Independence",12,1],["Finland","Independence Day",12,6],["Tanzania","Independence Day",12,9],["Burkina Faso","Proclamation of Independence Day",12,11],["Kenya","Jamhuri Day (Independence)",12,12],["Bahrain","Independence Day",12,16],["Kazakhstan","Independence Day",12,16],["Libya","Independence Day",12,24],["Slovenia","Independence and Unity Day",12,26],["Mongolia","Independence Day",12,29]]; /* [country, event, month, day] */

var THEMES={
 default:null,
 holi:{accent:"#f472b6",accent2:"#facc15",bg:"#1a0b1e",card:"#26112b",card2:"#311a38",line:"#4d2a5a"},
 diwali:{accent:"#fbbf24",accent2:"#fb923c",bg:"#1a1005",card:"#261a0a",card2:"#32230f",line:"#54391a"},
 eid:{accent:"#34d399",accent2:"#a7f3d0",bg:"#07170f",card:"#0d2418",card2:"#133121",line:"#1f5238"},
 xmas:{accent:"#f87171",accent2:"#4ade80",bg:"#150a0a",card:"#221111",card2:"#2e1818",line:"#4d2626"},
 saffron:{accent:"#fb923c",accent2:"#fbbf24",bg:"#190e06",card:"#25160a",card2:"#311e0f",line:"#523219"},
 india:{accent:"#ff9933",accent2:"#22c55e",bg:"#0b1220",card:"#121c30",card2:"#17233b",line:"#22314f"},
 nation:{accent:"#60a5fa",accent2:"#e8eefc",bg:"#0b1220",card:"#121c30",card2:"#17233b",line:"#22314f"},
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
 ["bg","card","card2","line","text","mute"].forEach(function(k){if(t[k])s.setProperty("--"+k,t[k])});
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
  return{kind:"fest",theme:indiaInd?"india":(names.length?th:"nation"),title:"Shubhkamnayein",text:parts.join(" - ")}}
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
 el.className="festbar "+p.kind;el.hidden=false;
 el.innerHTML="";var b=document.createElement("b");b.textContent=p.title+": ";el.appendChild(b);el.appendChild(document.createTextNode(p.text));
}
function clock(){
 var el=document.getElementById("liveclock");if(!el)return;
 function t(){var d=new Date();el.textContent=d.toLocaleDateString(undefined,{weekday:"long",day:"numeric",month:"long",year:"numeric"})+" - "+d.toLocaleTimeString()}
 t();setInterval(t,1000);
}
function whatsNew(){
 var box=document.getElementById("whatsnew-list");if(!box)return;
 var repo="prayagideepak-collab/-Battery-Sentinel-Pro-Netra";
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
