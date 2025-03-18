# FB Pages Discord Bot

The bot gets posts from the Facebook pages given and reposts them to
specified Discord channels.

It uses official Facebook API to get the posts.
You can either host it yourself, or contact me, add the Discord bot and enjoy.
Translations from Czech are missing in both variants, feel free to create a PR.

## TODO

- add pictures into README
- explicit post refresh request (updates the current post or posts a new one if the old one is deleted)
- some bulk delete option/clear channel option

## DC bot

You can add the bot hosted by me to your server using this
[invite link](https://discord.com/oauth2/authorize?client_id=1252917635948216401).
Make sure bot has access to the channels where you want to relay the posts (see bellow)
Use the `fb_ping` command to test if the bot works.
Current refresh period can be seen when a new page is added.
Also, in case this bot becomes popular the rate limit will be reached
as login is not enabled for my instance - I can handle ~40 pages.
See also events section for details as events don't work correctly in this mode.
I have plans to obtain necessary permissions from Facebook to overcome this issue.

If the bot does not work for you, please open an issue as it's still in a beta stage.
The bot is provided with no guaranties, see License.

Bot support multiple commands. Their availability may vary based on which FB authorization
features are enabled.

- `fb_ping` - tests connection and basic permissions
- `fb_list_available` - pages that are available to the bot. If the bot has proper rights,
  all the facebook pages are accessible.
- `fb_list_local` - list pages that are relayed to this channel.
- `fb_search` - searches FB for the given name and shows id's of the related pages
- `fb_add_page` - add page posts to this channel, use page id, url name, ... Comma separated list is also supported.
- `fb_remove_page` - removes pages from this channel, same interface as `fb_add_page`.
- `fb_authorize_login` - shows link that can be used to log into the app and authorize more pages
- `fb_authorize_user` - authorize pages of the user given by a `user_access_token`. System user token is also supported.

The bot needs following permission in the channel it's supposed to send messages into:

- view channel
- send messages
- embed links
- attach files
  The following permissions will be required in the future, so better add them now
  (basic functionality will work event without them)
- manage messages
- read message history
- create events
- manage events

### Events and embeds

If the bot is running in public content mode,
it cannot access post details like events or previews of the included links.
Bot tries to overcome this by searching for links in the post's text
and at least trying to make an embed out of them (using a Discord's mechanisms).
To access the posts, the page needs to be authorized by some of its admins logging in
or by using a system user of the portfolio.
`page_events` permission is most probably required.

### Removing access to your pages

To remove the access you granted this app to some of your pages,
do the according action in Facebook admin panel and just invalidate tokens.
If the bot runs in public content mode, make your page private,
otherwise the bot will get your page anyway.
If you logged in using Business login,
authenticate again and in list of pages the bot can manage select none.
If you used system user token, revoke tokens of the system user.

## Own deployment and Authorization

The server is configured using envirinment variables.
All the variables have to be set.
See `env_example` with configuration example.

### Facebook Graph API

Head to [Facebook developer page](https://developers.facebook.com),
create a **business** Facebook app and fill in basic information.
There are three options how to set up Facebook app permissions:

#### Page Public Content Access

The easiest way to set up the bot is to
obtain [Page Public Content Access](https://developers.facebook.com/docs/features-reference/page-public-content-access)
feature.
App review and business verification is needed for this feature,
you should be able to pass it quite easily.
With this permission, you can access any public page and bring it over to Discord

#### Pages of a logged-in user

Add business (not user) login feature to your app and fill in related settings.
App review, business verification and tech provider verification (hard to get) is needed for this feature.
Then, if a user logs into your app, you can access all the pages he has access to.
You can access pages where one of the app's developers/testers is admin without verification.
Therefore, to bypass app review and business verification, you need to become admin of the page or
one of the admins needs to become your app tester.
This app shows the auth link (if the module is set up) at startup, send this link to users to log in.
Required permissions are `pages_show_list`, `page_events` and `pages_read_engagement`.

#### Pages of a business portfolio

This option is similar to the previous one. In your [Meta Business Suit](https://business.facebook.com/latest),
head into the system users section. Make sure that your portfolio manages the given pages.
Create a new system user, give it permissions for bot the bot and pages.
Then click the bot, give it the same permissions as in the approach above and pass the token to this server.

### Discord bot

Create a Discord bot. Send invitation to a Discord admin with the following permissions:

- **TODO**

### Setup

Once you have a Discord bot and FB app created, build a Docker image
from this repository and run it with environment variables according
to `.env_example`. All the fields from this category are required as I'm lazy to set defaults.

### Management

The management is handled using three simple endpoints.
You can also edit the database directly as the schema is really simple.
All the `/admin` endpoints are secured by the access token set in your environment variables.
Pass it as a URL param `access_token=YOUR_TOKEN`.

- `POST /admin/:channel_id/:page_id` - Link the page to the channel
- `DELETE /admin/:channel_id/:page_id` - Unlink the page to the channel
- `GET /admin/status` - Lists authorized pages and channels they are linked to.

I recommend using cURL or Postman for this.

### Docker Compose

We provide example Docker Compose file bellow:

```docker-compose
services:
  bot:
    build: https://github.com/Lastaapps/fb-pages-discrod-bot.git#main
    restart: always
    env_file:
      - env
    ports:
      - 127.0.0.1:8080:8080
    volumes:
      - ./storage:/storage:rw
```

```env
FB_DC_API_SERVER_HOST=localhost
FB_DC_API_SERVER_PORT=8080
FB_DC_API_DATABASE_FILENAME=/storage/database.db
...
```

## Scraping (deprecated)

In this mode the app scrapes data from the `mbasic.facebook.com` Facebook frontend.
This mode is no longer supported nor maintained, but it may still work.
A fake account that will probably get banned quite quickly is needed.

### Deployment

Configuration is done using environment variables,
see `env_example` in git history.
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
