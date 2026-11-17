---
description: Find your Discord user ID so the bot knows you're its owner.
---

# Find your user ID

The bot's owner can use owner-only commands, gets update notices, and can always control the music. The bot knows
it's you by your Discord **user ID**: a number 17 to 20 digits long, such as `123456789012345678`. It isn't your
username.

## Turn on Developer Mode

Discord only shows IDs once Developer Mode is on.

1. In Discord, open **User Settings** (the gear next to your name).
2. Go to **Advanced**.
3. Turn on **Developer Mode**.

<figure class="oa-media oa-media--pending" markdown>
Screenshot: User Settings, Advanced, with Developer Mode on
</figure>

## Copy your ID

1. Click your avatar in the bottom-left corner of Discord, or find yourself in a server's member list.
2. Right-click your name (long-press on a phone) and choose **Copy User ID**.

<figure class="oa-media oa-media--pending" markdown>
Screenshot: right-click menu on your name with Copy User ID
</figure>

You'll paste this number when the bot asks for the owner on its first run, or into `config.txt`:

```hocon
discord {
  owner = 123456789012345678
}
```

The owner value is a plain number, without quotes.

!!! tip "Not the bot's ID"
    Make sure you copy **your** ID, not the bot's or the server's. If owner-only commands such as `shutdown` say
    you don't have permission, the ID is probably wrong.

Next: [Run the bot for the first time](first-run.md).
