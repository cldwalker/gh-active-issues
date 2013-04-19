## Description

This is an app to let users know what number they are in my list of
active github issues. Unfortunately, github neither provides a public
view of a maintainer's
[issues dashboard](https://github.com/dashboard/issues/repos)
nor provides a view for a maintainer to see their active issues across repositories
(active != open). This is important because without these abilities, maintainers
increasingly ignore most of their issues and users often have unrealistic
expectations of turnaround time and feel ignored. Let's fix this!

My current active issues [live on heroku](https://gh-waiting-room.herokuapp.com/).

## Features

This app has three main features:

* It lists issues the maintainer considers active.
* It auto-comments soon after an issue/pull request is opened with a
  link to the issue on the active issues list. This link is unique and
  remains valid as long as the issue is active.
* The active issues are auto-updated any time an issue is closed or opened.

The second and third features are enabled per repository using service
hooks. To see these features in action feel free to open/close an
issue on
[this repository](https://github.com/cldwalker/gh-waiting-room/issues).
To set up service hooks see [comment-bot](#comment-bot).

## Running the App

1. Start the application: `GITHUB_AUTH=my-basic-auth GITHUB_USER=my-name lein run`
2. Go to [localhost:8080](http://localhost:8080/).
3. The first time the home page loads, it takes awhile since it's
   fetching the issues. Issues are saved in memory so subsequent reloads
   are instant.

## Running on Heroku

I encourage you to fork and give it a shot:

```sh
$ heroku create
$ heroku config:set GITHUB_AUTH=my-auth GITHUB_USER=my-name
$ git push heroku master
$ heroku open
```

## Configuration

The following environment variables allows you to configure what user
and issues the public can see:

* $GITHUB_AUTH (required) - This is your username:password basic auth
  [as described in github's docs](http://developer.github.com/v3/#authentication)
* $GITHUB_USER (required) - Your github username
* $GITHUB_ISSUE_REGEX - A string that's interpreted as a regex to
  filter what repositories are viewable. This defaults to
  github.com/USERNAME. Since this app fetches issues across all orgs
  or repositories a user has access to, you can potentially allow
  users to see your repositories plus whatever other organization
  repositories you'd like e.g. github.com/(cldwalker|pedestal).
* $GITHUB_HIDE_LABELS - Given a comma delimited list of labels, it
  hides issues with those labels. Useful when labels indicate issues
  that you're not actively working on but have left open. If nothing
  is specified, the default is to hide issues with any labels.
* $APP_DOMAIN - This is required if you're using the auto-commenting.
  This should be the full domain of your app and will be used mainly
  for link generation.

## Who this is for

If you're a user or organization who does any of the following:
* strives to respond to all their issues
* treats all their active repositories equally and runs through issues in the
  order they are created
* likes to leave issues open for others but has no intention of
  working on them (use labels and $GITHUB_HIDE_LABELS)
* likes to only see a subset of all repositories' issues (use
  $GITHUB_ISSUE_REGEX)

then this is for you.

## Credits
* Joshua Johnson for providing list styling in
  [this article](http://designshack.net/articles/css/5-simple-and-practical-css-list-styles-you-can-copy-and-paste/)
  
## TODO
* Add optional ability to use
  [secret](https://github.com/github/github-services/blob/master/lib/services/web.rb#L7)
  with /webhook
* Lein task to be run nightly for adding webhooks to new repositories
