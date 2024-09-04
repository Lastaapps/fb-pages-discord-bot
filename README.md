# FB Pages Discord Bot

The bot gets posts from the Facebook pages given and reposts them to 
specified Discord channels. It comes in two variants:
Facebook Graph API where you need the page admins to log in to the app
and scraping, that needs a bot account that will probably get banned quite quickly.
No one said it's going to be easy.

## Facebook Graph API

This is the preferred mode.
After you set up the server, a link will be printed to the console.
You send the link to page admins, they get redirected to a Facebook authorization
page where they give your app a basic permission.
Then you create a Discord bot and add it to your server.
The app has no frontend for assigning pages to channels,
all this is done using the API described bellow.

### Facebook APP

You log into Facebook developer account and create a business app (you don't need a business).
You enable Facebook Business Login (if it is not presented, you created a wrong app type),
add redirect URL and create a configuration with `pages_show_list` permission.
Facebook login required you to use https instead of http (you should do it anyway).

### Setup

Once you have a Discord bot and FB app created, build a Docker image
from this repository and run it with environment variables according
to `.env_example`. All the fields from this category are required.

### Management

The management is handled using three simple endpoints.
You can also edit the database directly as the schema is really simple.
All the `/admin` endpoints are secured by the access token set in your environment variables.
Pass it as a URL param `access_token=YOUR_TOKEN`.

- `POST /admin/:channel_id/:page_id` - Link the page to the channel
- `DELETE /admin/:channel_id/:page_id` - Unlink the page to the channel
- `GET /admin/status` - Lists authorized pages and channels they are linked to.


#### Disclaimer

The code for this part is quite a spaghetti one, I'm aware of it, and I'm not 
changing it as I don't have time for it and this is enough.

## Scraping

In this mode the app scrapes data from the `mbasic.facebook.com` Facebook frontend.
This mode is no longer supported nor maintained, but it may still work.

### Deployment
First you need to create a bot account and
set up the account's translation options
https://m.facebook.com/settings/language/auto_translate_disabled_dialects/

After that, create a Discord bot, get the token and add the bot to the desired server.

Then set up your environment variables according to `.evn_example`.
Log into your account, take the cookie you got, take the desired fields (url decode them).
After that specify bot's Discord token, channel ID to post the posts into,
delay how ofter a full scan will happen and weight of your mum.
*Don't use your main account as it may get banned.*

#### Race Conditions
It may happen that if a post is at the time of server startup
published n hours ago,
some other posts may be skipped from being posted/posted twice.
To minimize the chance of this happening, start the bot during weekend.

## Development
I recommend EnvFile plugin for developing this project.

## License
The app is licensed under the `GNU GPL v3.0` license.
