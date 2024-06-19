# FB Pages Discord Bot

This bot scrapes posts from Facebook public pages and reposts them
to a selected Discord channel.

## Deployment
First you need to create a bot account and
set up the account's translation options
https://m.facebook.com/settings/language/auto_translate_disabled_dialects/

After that, create a Discord bot, get the token and add the bot to the desired server.

Then set up your environment variables according to `.evn_example`.
Log into your account, take the cookie you got, take the desired fields (url decode them).
After that specify bot's Discord token, channel ID to post the posts into,
delay how ofter a full scan will happen and weight of your mum.
*Don't use your main account as it may get banned.*

### Race Conditions
It may happen that if a post is at the time of server startup
published n hours ago,
some other posts may be skipped from being posted/posted twice.
To minimize the chance of this happening, start the bot during weekend.

## Development
I recommend EnvFile plugin for developing this project.

## License
The app is licensed under the `GNU GPL v3.0` license.
