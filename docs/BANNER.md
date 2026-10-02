# Website banner, theme, clock and "What's new"

All of this lives in `docs/banner.js` and is shown by `docs/index.html`.

## What it does
- **Live date and time**: ticks every second in the visitor's own timezone (client side only).
- **What's new**: the latest 3 releases, read live from the GitHub releases API. It updates by itself after every release. If GitHub cannot be reached it says so.
- **Festival banner and theme**: on a festival day the banner shows it and the site colours change to match (Holi pink, Diwali gold, Eid green, Christmas red/green, and so on). For the 3 days before a festival it shows "Aane wala". On other days there is no banner and the normal theme stays. Nothing is invented.
- **Independence Days**: every country's Independence Day (fixed month and day) shows on its day. India comes first and gets saffron and green.
- **Never**: Pakistan (owner's rule: no Pakistan flag or Pakistan-related theme or banner anywhere; Pakistan is removed from the Independence Day list), and death anniversaries. They are not in the data and must not be added.

## Where the data comes from
- Festivals: timeanddate.com India calendar for 2026 and 2027, with Equinox, Solstice, Father's/Mother's Day, Halloween, Valentine's Day, Muharram/Ashura and martyrdom days removed. Moon-based dates (Eid, Ramadan, Milad un-Nabi) can differ by a day depending on the region; the banner says "date may differ by a day" where the source marks a date tentative.
- Independence Days: Wikipedia "List of national independence days", only entries with a fixed month and day, plus Kenya, Fiji and South Korea added by hand. Israel (Hebrew calendar), Cuba, Dominican Republic, Palestine and Colombia are not included because the list entry was unclear. This is about 140 countries, not literally every country.
- After 2027 there is no festival data, so no festival banner is shown until the lists are extended (add rows to `FEST`, format `[yyyymmdd, "Name", 0 or 1 if the date may shift]`).

## Adding a condolence (shok sandesh) banner by hand
Use this only when a genuinely important person of any country has died (not anniversaries). Open `docs/banner.js`, find `var CONDOLENCES=[];` and add one entry, then open a pull request:

```js
var CONDOLENCES=[
  {from:"2026-10-05", to:"2026-10-08", name:"Full name", country:"Country", text:"Short respectful line"}
];
```

- `from` and `to` are inclusive dates (`yyyy-mm-dd`). A condolence overrides any festival while it is active, and the whole site switches to a somber grey/black theme.
- Keep the text short and factual. Check the news from a reliable source first.
- Remove the entry after it expires (optional; it stops showing by itself after `to`).
