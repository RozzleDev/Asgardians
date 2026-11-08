# Asgardians Clan

A RuneLite plugin for members of the Old School RuneScape clan [Asgardians](https://asgardiansclan.com).

It records your drops, pets, collection log, deaths and personal bests for the clan, and shows clan events, teams, sign-ups and announcements in a side panel. There's no login: the plugin recognises you by your RSN on the clan roster.

## What it does

- **Records** loot, pets, new collection log items, deaths and personal bests, with screenshots.
- **Syncs** your personal bests and collection log, which is fetched automatically when you open it.
- **Home:** the side panel opens on a page of tiles, one for each page below, each with a live line such as "3 open" or "2 new". The line at the bottom shows whether you're connected, and who as; the cog at the top opens the plugin's settings as switches.
- **Bingo:** the running event's board as tiles with item pictures, progress strips and finished lines in gold (boards up to 12×12), each task as a card (hide completed ones), every team's score, and your team with who's online. Between events it shows the next bingo with sign-ups.
- **Events:** a **Current event** page that breaks the running event down by team, with who's online, each team's board, the standings, recent drops and drops waiting for you to pick a tile, and a **Schedule** page of bingo events, Boss of the Week, Skill of the Week and other clan events, with sign-ups.
- **Event messages:** a chat message as soon as one of your drops counts towards a tile, then messages when any team in your event submits an item, has one accepted or completes a tile.
- **Team badges** in clan chat during an event, in place of each participant's rank icon.
- **Groups:** find or start a group for raids, bosses and other group content. Join with one click, see who's online, and get put in the group's RuneLite party. A chat message tells the clan when a group starts (pick which activities with the bells in "Group alerts"; clan staff can send some to everyone), and the group when it fills and which world to hop to.
- **Handeggs:** throw an Easter handegg at a clanmate and the clan hears about it (eggs to or from non-members don't count): a chat message for everyone with the plugin, an Ice Barrage freeze sound for whoever got egged, and Eggers and Most egged leaderboards.
- **Drops:** the clan's latest drops (any value, 1M+, 10M+ or 100M+), pets and new collection log slots, 25 of each, as cards with the item, who got it, when, and tags such as CoX or Pet.
- **PBs:** the clan's record times by boss and team size, picked from drop-downs, with the top ten.
- **Members:** clanmates online now, their world, and what they're doing (a raid, a boss, a skill) when they share it. Click anyone for their clan profile.
- **Leaderboard:** the current Boss or Skill of the Week, plus clan credit, collection log, drops and deaths for this month, this year or all time, picked from a drop-down. Each board folds away.
- **News:** clan announcements. Unread ones are shown when you log in.
- **Event codeword overlay** for events that use one.
- **Profile:** your clan rank, time in the clan, Discord, clan credit, EHB and EHP, rankings, events, PBs, collection log and pets, drops, deaths and handeggs, and whether PBs and the collection log are syncing.
- **Bingo tracking:** kill counts, XP and finished quest speedruns are sent too, so bingo tasks like "Kill Vorkath 250 times", "Gain 5M Slayer XP" or "Gold on Dragon Slayer I" count automatically.

## Data sent

The plugin sends your IP address, RSN, account hash, gameplay data (drops, pets, collection log, deaths, personal bests, kill counts, skill XP, quest speedrun times and handegg throws, with screenshots), group finder activity (the groups you start or join, your world and kill count for that content) and, with "Share my activity" on, what you're doing in game and your world, for the Members page to asgardiansclan.com, or to the server address set in the plugin's settings. This is a 3rd-party server not controlled or verified by the RuneLite developers. Gameplay data is only sent if your RSN is on the clan roster.

Private messages are hidden in screenshots.

## Development

Build and run the tests with:

```bash
./gradlew build
```

Set **API address** in the plugin's settings, or use `-Dasgardians.apiUrl=<address>`, to point the plugin at a test server.

## License

[BSD 2-Clause](LICENSE)
