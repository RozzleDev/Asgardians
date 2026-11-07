# Asgardians Clan

A RuneLite plugin for members of the Old School RuneScape clan [Asgardians](https://asgardiansclan.com).

It records your drops, pets, collection log, deaths and personal bests for the clan, and shows clan events, teams, sign-ups and announcements in a side panel. There's no login: the plugin recognises you by your RSN on the clan roster.

## What it does

- **Records** loot, pets, new collection log items, deaths and personal bests, with screenshots.
- **Syncs** your personal bests and collection log, which is fetched automatically when you open it.
- **Events tab:** a **Current event** page that breaks the running event down by team, with who's online, each team's board, the standings, recent drops and drops waiting for you to pick a tile, and a **Schedule** page of bingo events, Boss of the Week, Skill of the Week and other clan events, with sign-ups.
- **Event messages:** a chat message as soon as one of your drops counts towards a tile, then messages when any team in your event submits an item, has one accepted or completes a tile.
- **Team badges** in clan chat during an event, in place of each participant's rank icon.
- **Groups tab:** find or start a group for raids, bosses and other group content. Join with one click, see who's online, and get put in the group's RuneLite party. A chat message tells the clan when a group starts (pick which activities with the bells in "Group alerts"; clan staff can send some to everyone), and the group when it fills and which world to hop to.
- **Handeggs:** throw an Easter handegg at a clanmate and the clan hears about it (eggs to or from non-members don't count): a chat message for everyone with the plugin, an Ice Barrage freeze sound for whoever got egged, and Eggers and Most egged leaderboards.
- **Stats tab:** the clan's latest drops (all, 1m+, 10m+ or 100m+), new collection log slots, pets and new PBs (each member's best time only), 25 of each, with who got them and when.
- **Leaderboard tab:** the current Boss or Skill of the Week, plus clan credit, collection log, drops and deaths for this month, this year or all time. Each board folds away.
- **News tab:** clan announcements. Unread ones are shown when you log in.
- **Event codeword overlay** for events that use one.
- **Status tab:** your connection, plus your clan rank, credits and totals.

## Data sent

The plugin sends your IP address, RSN, account hash, gameplay data (drops, pets, collection log, deaths and personal bests, with screenshots) and group finder activity (the groups you start or join, your world and kill count for that content) to asgardiansclan.com, or to the server address set in the plugin's settings. This is a 3rd-party server not controlled or verified by the RuneLite developers. Gameplay data is only sent if your RSN is on the clan roster.

Private messages are hidden in screenshots.

## Development

Build and run the tests with:

```bash
./gradlew build
```

Set **API address** in the plugin's settings, or use `-Dasgardians.apiUrl=<address>`, to point the plugin at a test server.

## License

[BSD 2-Clause](LICENSE)
