# Asgardians Clan

A RuneLite plugin for members of the Old School RuneScape clan [Asgardians](https://asgardiansclan.com).

It records your drops, pets, collection log, deaths and personal bests for the clan, and shows clan events, teams, sign-ups and announcements in a side panel. There's no login: the plugin recognises you by your RSN on the clan roster.

## What it does

- **Records** loot, pets, new collection log items, deaths and personal bests, with screenshots.
- **Syncs** your personal bests and collection log, which is fetched automatically when you open it.
- **Events tab:** bingo events, Boss of the Week, Skill of the Week and other clan events, with sign-ups and your team.
- **Leaders tab:** the current Boss or Skill of the Week, plus this month's clan credit, collection log and drops.
- **News tab:** clan announcements. Unread ones are shown when you log in.
- **Event codeword overlay** for events that use one.
- **Status tab:** your connection, plus your clan rank, credits and totals.

## Data sent

The plugin sends your IP address, RSN, account hash and gameplay data (drops, pets, collection log, deaths and personal bests, with screenshots) to asgardiansclan.com, or to the server address set in the plugin's settings. This is a 3rd-party server not controlled or verified by the RuneLite developers. Gameplay data is only sent if your RSN is on the clan roster.

Private messages are hidden in screenshots.

## Development

Build and run the tests with:

```bash
./gradlew build
```

Set **API address** in the plugin's settings, or use `-Dasgardians.apiUrl=<address>`, to point the plugin at a test server.

## License

[BSD 2-Clause](LICENSE)
