# Hit Counter
Hit Counter is a client-side mod that tracks PvP hits between you and your opponent(s). A positive number shows how many hits you're ahead of them, and a negative number shows how many hits you are behind. By default, the mod counts critical hits, normal hits and weak hits.

![Preview](https://cdn.modrinth.com/data/E2594jGq/images/6f003bd244f4a8ba4baa3867a037aad5f35e99fc.webp)

## Customizability
The mod has many settings to personalize it, including:
- Placing the counter to the left, right, above or below a player's username
- Setting separate values for normal, critical and weak hits
- Auto resets on death/respawn and inactivity
- Changing colors for positive, negative and *zero balances (if enabled)*
- Configurable whitelist/blacklist for hit sources that count towards the hit balance (wind charges and thorns disabled by default), with support for **some** custom server abilities
- Server overrides for custom per-server settings.

Have a feature idea? Open an issue!

## Controls and commands
Reset all balances with the keybind in Controls or `/hitcounter reset`. Press the key or run the command again within five seconds to undo the reset. Your previous balances return, and any hits registered since the reset are kept.
To clear one player's balance, use `/hitcounter reset <player>` or click their name in `/hitcounter stats`, which lists players with registered hits and their current balances.

## Flashback Integration
When enabled, Hit Counter saves hit balances alongside your Flashback replay, including balances already present when recording starts. The setting must be enabled before starting a recording, otherwise the replay won't contain hit balances.

## Session summary
View session totals and your highest and lowest balances in the pause menu or after an unexpected disconnect. Session results survive balance resets, and each server’s session is kept until you close Minecraft.

##

### Known Limitations

Hit Counter can only count damage the client receives and can attribute to a player. Some custom attacks, effects such as poison, or server-specific mechanics may be missed.

---

## License

This project is licensed under the [Hit Counter License](LICENSE). Monetised online content featuring the mod is permitted with credit and a link to its Modrinth page. The unmodified mod may be included in monetised modpacks without additional attribution; standalone commercial distribution, reuploads, and modified redistribution require permission.
