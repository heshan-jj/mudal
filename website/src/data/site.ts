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
  { href: "#privacy", label: "Privacy" },
  { href: "#download", label: "Download" },
  { href: "#faq", label: "FAQ" },
  { href: "#about", label: "About" },
];

/** Stroke icons, 24×24 viewBox, drawn for this site. */
export const icons = {
  sms: "M4 5h16v11H8l-4 4V5z M8 9h8 M8 12h5",
  wallet: "M3 7h15a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7z M3 7l12-4v4 M16 13.5h.01",
  pie: "M12 3a9 9 0 1 0 9 9h-9V3z M15 3.5A9 9 0 0 1 20.5 9H15V3.5z",
  target: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 12h.01",
  repeat: "M17 2l4 4-4 4 M3 11V9a3 3 0 0 1 3-3h15 M7 22l-4-4 4-4 M21 13v2a3 3 0 0 1-3 3H3",
  rules: "M4 6h10 M4 12h7 M4 18h12 M17 4l3 3-3 3 M14 10l3 3-3 3",
  chart: "M4 20V10 M10 20V4 M16 20v-7 M22 20H2",
  lock: "M6 11h12v10H6V11z M8 11V7a4 4 0 0 1 8 0v4 M12 15v2",
  widget: "M4 4h7v7H4V4z M13 4h7v7h-7V4z M4 13h7v7H4v-7z M13 13h7v7h-7v-7z",
  backup: "M12 3v12 M7 10l5 5 5-5 M4 17v3h16v-3",
  sparkles: "M12 3l1.8 4.7L18.5 9.5l-4.7 1.8L12 16l-1.8-4.7L5.5 9.5l4.7-1.8L12 3z M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15z",
  split: "M6 3v6a6 6 0 0 0 6 6 6 6 0 0 0 6-6V3 M12 15v6",
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
    body: "Card payments, ATM withdrawals and transfers become transactions the moment the SMS arrives. No typing, no bank login.",
  },
  {
    icon: "wallet",
    title: "Every account in one place",
    body: "Savings, current and credit-card accounts are picked up from the SMS, with balances and balance history.",
  },
  {
    icon: "pie",
    title: "Budgets that keep up",
    body: "Weekly, monthly or one-off budgets over the categories you choose, with progress that updates as you spend.",
  },
  {
    icon: "target",
    title: "Savings goals",
    body: "Put money aside for the insurance bill or the Ella trip. See what you have saved and whether you are on pace.",
  },
  {
    icon: "repeat",
    title: "Subscriptions & recurring",
    body: "Track Netflix, Dialog and the electricity bill, and schedule the things your bank never texts you about.",
  },
  {
    icon: "chart",
    title: "Clear analytics",
    body: "Category breakdowns, trends and month-on-month comparisons, so you can see where the rupees actually go.",
  },
  {
    icon: "rules",
    title: "Your rules",
    body: "Auto-categorise by merchant, amount or text. Unlimited rules, applied to new and past transactions.",
  },
  {
    icon: "split",
    title: "Loans & split bills",
    body: "Keep track of money lent and borrowed, and split a dinner bill without a spreadsheet.",
  },
  {
    icon: "widget",
    title: "Home-screen widgets",
    body: "Recent transactions, a category chart and your goals, right on the home screen.",
  },
  {
    icon: "backup",
    title: "Backups & CSV export",
    body: "Export everything to a file you control, schedule daily backups to a folder, or export CSV for a spreadsheet.",
  },
  {
    icon: "lock",
    title: "App lock & hidden previews",
    body: "Lock Mudal behind your fingerprint or PIN, and hide its contents in the recent-apps switcher.",
  },
  {
    icon: "sparkles",
    title: "Optional AI assistant",
    body: "Ask “how much did I spend on food last month?” using your own Google Gemini key. Off unless you turn it on.",
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
    a: "Not unless you turn on a feature that needs the internet. SMS parsing, categories, budgets and the database all stay on the device. The only network calls are the ones you opt into: exchange rates for multi-currency, the Gemini assistant with your own key, webhooks you configure, and a pre-filled GitHub issue when you report an unrecognised SMS. The privacy policy below lists each one.",
  },
  {
    q: "Is it really free? What is the catch?",
    a: "There is no catch. Mudal has no ads, no subscription, no in-app purchases and no Pro tier — every feature is unlocked. It is open source under the AGPL-3.0, so the code is public and anyone can check what it does.",
  },
  {
    q: "Android won’t let me allow SMS permission. What do I do?",
    a: "On Android 14 and newer, apps installed from outside the Play Store have SMS access restricted by default. Open Settings → Apps → Mudal, tap the ⋮ menu in the top corner and choose “Allow restricted settings”, then grant SMS permission. Mudal shows a button that takes you straight to that screen.",
  },
  {
    q: "My bank isn’t supported. Can you add it?",
    a: "Yes, with your help. Parsers are only written from real messages, never guessed. Open a bank request on GitHub with a few of your bank’s transaction SMS — with account numbers and names masked — and say which sender ID they come from. Seylan, HNB, Bank of Ceylon, People’s Bank, DFCC and Pan Asia are next in line once samples arrive.",
  },
  {
    q: "Will my data survive if I change phones?",
    a: "Use Settings → Data Management to export a backup file and import it on the new phone. The database itself is encrypted with a key that never leaves the old device, so a backup file is the way to move. Backup files are not encrypted, so keep them somewhere private.",
  },
  {
    q: "Do I need the AI assistant?",
    a: "No. The assistant is optional and off by default. It only works after you paste in your own free Google Gemini API key, and it only sends the chat and the spending details it needs to answer you, directly to Google.",
  },
  {
    q: "Is Mudal on the Play Store?",
    a: "Not yet. Google has strict rules for apps that read SMS, so Mudal is published on GitHub Releases for now. Each release is signed with the same key and comes with a SHA-256 checksum, so updates install over the old version and keep your data.",
  },
  {
    q: "Is there an iPhone version?",
    a: "No. iOS does not let apps read SMS, which is what Mudal is built around, so it is Android only.",
  },
  {
    q: "How is Mudal related to PennyWise AI?",
    a: "Mudal is an independent fork of the open-source PennyWise AI tracker, reworked for Sri Lanka: Sri Lankan bank parsers, LKR defaults, no paywall and an encrypted database. It is not affiliated with or endorsed by the PennyWise project.",
  },
];
