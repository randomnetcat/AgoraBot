# AgoraBot

AgoraBot is a Discord bot that was written for the Agora Nomic Chat Server.

## Features

* IRC<->Discord bridging
* Permissions
* Digests that can be emailed
* Judge lists

## Running

Example commandline:

```bash
build/install/AgoraBot \
    --token "<YOUR_TOKEN_HERE>" \
    --data-version 1 \
    --config-path "<RUN_DIR>/config" \
    --storage-path "<RUN_DIR>/storage" \
    --temp-path "<RUN_DIR>/tmp"
```

Parameter meanings:

* `token`: Your Discord token
* `data-version`: `1` for now. Higher numbers may be used in the future for incompatible configuration format changes.
* `config-path`: The path to a directory with configuration files in it.
* `storage-path`: The path to a directory where generated files will be stored (e.g. the prefix map).
* `temp-path`: The path to a directory for temporary files to be stored.

## Configuration

No configuration files are needed for the bot to run, but it won't be as useful.

### Permissions

Path: `config/permissions.json` \
Contents: A JSON file \
Format:

```json
{
  "admins": [
    "BOT_ADMIN_DISCORD_ID_A",
    "BOT_ADMIN_DISCORD_ID_B",
    "..."
  ]
}
```

### IRC<->Discord Relay

Path: `config/relay.json` \
Contents: A JSON file \
Format:

```json
{
  "setup": {
    "irc_servers": {
      "server_a": {
        "host": "irc.example.com",
        "port": 6697,
        "secure": true,
        "user_nickname": "SomeNickname"
      },
      "server_b": {
        "host": "irc2.example.com",
        "port": 6697,
        "secure": true,
        "user_nickname": "AnotherNickname"
      }
    }
  },
  "endpoints": {
    "irc": {
      "some_irc_channel": {
        "server_name": "server_a",
        "channel_name": "##whatever",
        "command_prefix": "!"
      },
      "another_irc_channel": {
        "server_name": "server_b",
        "channel_name": "##something"
      }
    },
    "discord": {
      "some_discord_channel": {
        "channel_id": "YOUR_DISCORD_CHANNEL_ID"
      }
    }
  },
  "bridges": [
    {
      "endpoints": [
        "some_irc_channel",
        "another_irc_channel",
        "some_discord_channel"
      ]
    }
  ]
}
```

## Other

TODO: Other configuration (digest, citations, etc.)
