export const REPO_URL = "https://github.com/heshan-jj/mudal";
export const RELEASES_URL = `${REPO_URL}/releases/latest`;
export const ISSUES_URL = `${REPO_URL}/issues`;
export const NEW_ISSUE_URL = `${REPO_URL}/issues/new/choose`;
export const BANK_REQUEST_URL = `${REPO_URL}/issues/new?template=bank_support_request.md`;
export const LICENSE_URL = `${REPO_URL}/blob/main/LICENSE`;
export const UPSTREAM_URL = "https://github.com/sarim2000/pennywiseai-tracker";

export const PRIVACY_EFFECTIVE_DATE = "22 October 2026";

export const nav = [
  { href: "#features", label: "Features" },
  { href: "#sri-lanka", label: "Banks" },
  { href: "#download", label: "Download" },
  { href: "#faq", label: "FAQ" },
];

/** Stroke icons, 24×24 viewBox, drawn for this site. */
export const icons = {
  sms: "M4 5h16v11H8l-4 4V5z M8 9h8 M8 12h5",
  bank: "M11.12 2.2a2 2 0 0 1 1.76 0l7.87 3.85c.48.23.31.95-.22.95H3.47c-.53 0-.7-.72-.22-.95z M6 18v-7 M10 18v-7 M14 18v-7 M18 18v-7 M3 22h18",
  pie: "M21 12c.55 0 1-.45.95-1A10 10 0 0 0 13 2.05c-.55-.05-1 .4-1 .95v8a1 1 0 0 0 1 1z M21.21 15.89A10 10 0 1 1 8 2.83",
  piggyBank: "M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z M16 10h.01 M2 8v1a2 2 0 0 0 2 2h1",
  repeat: "M17 2l4 4-4 4 M3 11v-1a4 4 0 0 1 4-4h14 M7 22l-4-4 4-4 M21 13v1a4 4 0 0 1-4 4H3",
  tag: "M12.59 2.59A2 2 0 0 0 11.17 2H4a2 2 0 0 0-2 2v7.17a2 2 0 0 0 .59 1.42l8.7 8.7a2.43 2.43 0 0 0 3.42 0l6.58-6.58a2.43 2.43 0 0 0 0-3.42z M7.5 7.5h.01",
  chart: "M3 3v16a2 2 0 0 0 2 2h16 M8 17v-3 M13 17V5 M18 17V9",
  lock: "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z M7 11V7a5 5 0 0 1 10 0v4",
  widgets: "M4 3h5a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M15 3h5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M15 12h5a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z M4 16h5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z",
  fileExport: "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z M14 2v4a2 2 0 0 0 2 2h4 M12 18v-6 M9 15l3 3 3-3",
  sparkles: "M9.94 15.5a2 2 0 0 0-1.44-1.44l-6.13-1.58a.5.5 0 0 1 0-.96L8.5 9.94A2 2 0 0 0 9.94 8.5l1.58-6.14a.5.5 0 0 1 .96 0l1.58 6.14a2 2 0 0 0 1.44 1.44l6.13 1.58a.5.5 0 0 1 0 .96l-6.13 1.58a2 2 0 0 0-1.44 1.44l-1.58 6.13a.5.5 0 0 1-.96 0z M20 3v4 M22 5h-4 M4 17v2 M5 18H3",
  users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  shield: "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z M9 12l2 2 4-4",
  eyeOff: "M3 3l18 18 M10.6 6.1A9.8 9.8 0 0 1 12 6c5 0 9 6 9 6a15.6 15.6 0 0 1-3 3.6 M6.6 6.6C4.3 8.1 3 12 3 12s4 6 9 6a8.9 8.9 0 0 0 4.4-1.2 M9.9 9.9a3 3 0 0 0 4.2 4.2",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0",
  code: "M8 7l-5 5 5 5 M16 7l5 5-5 5 M14 4l-4 16",
  gift: "M3 9h18v4H3V9z M5 13h14v8H5v-8z M12 9v12 M12 9c-2-4-6-4-6-1.5S10 9 12 9z M12 9c2-4 6-4 6-1.5S14 9 12 9z",
  download: "M12 3v13 M6 11l6 6 6-6 M4 21h16",
  github: "M9 19c-4 1.3-4-2-6-2.5 M15 22v-3.5a3 3 0 0 0-.9-2.4c3-.3 6.1-1.5 6.1-6.6a5.2 5.2 0 0 0-1.4-3.6 4.8 4.8 0 0 0-.1-3.6s-1.1-.3-3.7 1.4a12.8 12.8 0 0 0-6.6 0C5.8 1 4.7 1.3 4.7 1.3a4.8 4.8 0 0 0-.1 3.6A5.2 5.2 0 0 0 3.2 8.5c0 5.1 3.1 6.3 6.1 6.6a3 3 0 0 0-.9 2.4V22",
} as const;

export type IconName = keyof typeof icons;

export const features: { icon: IconName; title: string; body: string }[] = [
  {
    icon: "sms",
    title: "Automatic from bank SMS",
    body: "Card payments, ATM withdrawals and transfers become transactions the moment the SMS arrives.",
  },
  {
    icon: "bank",
    title: "Every account in one place",
    body: "Savings, current and credit-card accounts are picked up from the SMS, with balances and balance history.",
  },
  {
    icon: "pie",
    title: "Budgets that keep up",
    body: "Weekly, monthly or one-off budgets over the categories you choose, with progress that updates as you spend.",
  },
  {
    icon: "chart",
    title: "Clear analytics",
    body: "Category breakdowns, trends and month-on-month comparisons, so you can see where the rupees actually go.",
  },
  {
    icon: "tag",
    title: "Your rules",
    body: "Auto-categorise by merchant, amount or text, applied to new and past transactions.",
  },
];

/** Self-hosted marks for the marketing site only (see `public/banks/README.md`). */
export const banks = [
  { name: "Commercial Bank of Ceylon", short: "ComBank", slug: "combank", logo: "combank.svg" },
  { name: "Sampath Bank", short: "Sampath", slug: "sampath", logo: "sampath.png" },
  { name: "Nations Trust Bank", short: "NTB", slug: "ntb", logo: "ntb.png" },
  { name: "National Savings Bank", short: "NSB", slug: "nsb", logo: "nsb.png" },
  { name: "National Development Bank", short: "NDB", slug: "ndb", logo: "ndb.png" },
];

export const comingBanks = ["Seylan", "HNB", "Bank of Ceylon", "People’s Bank", "DFCC", "Pan Asia"];

export const faqs: { q: string; a: string }[] = [
  {
    q: "Why does Mudal need permission to read my SMS?",
    a: "Reading bank SMS is how Mudal works. When your bank texts you about a transaction, Mudal reads that message on your phone and turns it into an entry. Messages are never sent anywhere. If you would rather not grant it, you can still add transactions by hand.",
  },
  {
    q: "Does any of my data leave my phone?",
    a: "Not unless you turn on a feature that needs the internet. SMS parsing, categories, budgets and the database all stay on the device. The only network calls are the ones you opt into: exchange rates, an AI assistant with your own key, webhooks you configure, and a pre-filled GitHub issue when you report an unrecognised SMS. The privacy policy lists each one.",
  },
  {
    q: "My bank isn’t supported. Can you add it?",
    a: "Yes, with your help. Parsers are only written from real messages, never guessed. Open a bank request on GitHub with a few of your bank’s transaction SMS — with account numbers and names masked — and say which sender ID they come from. Seylan, HNB, Bank of Ceylon, People’s Bank, DFCC and Pan Asia are next in line once samples arrive.",
  },
  {
    q: "Will my data survive if I change phones?",
    a: "Use Settings → Data Management to export a backup file and import it on the new phone. The database itself is encrypted with a key that never leaves the old device, so a backup file is the way to move. Backup files are not encrypted, so keep them somewhere private.",
  },
];
