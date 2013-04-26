## Description

This is an app to tell users where they are in my list of
active github issues. Unfortunately, github neither provides a public
view of a maintainer's
[issues dashboard](https://github.com/dashboard/issues/repos)
nor provides a view for a maintainer to see their active issues across repositories
(active != open). This is important because without these abilities, maintainers
increasingly ignore most of their issues and users often have unrealistic
expectations of turnaround time and feel ignored. Let's fix this!

My current active issues [live on heroku](https://gh-active-issues.herokuapp.com/).

[![Build Status](https://travis-ci.org/cldwalker/gh-active-issues.png?branch=master)](https://travis-ci.org/cldwalker/gh-active-issues)

## Features

This app has two main features:

* It lists issues the maintainer considers active.
* It auto-comments soon after an issue/pull request is opened with a
  link to the issue on the issues list (usually < 1min). This link is unique and
  remains valid as long as the issue is active. Also, active issues
  are auto-updated any time an issue is closed or opened.

The second feature is enabled per repository using webhooks. To see
these features in action feel free to open/close an issue on
[this repository](https://github.com/cldwalker/gh-active-issues/issues).
To set up webhooks see [webhooks](#webhooks).

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
$ heroku config:set GITHUB_AUTH=my-auth GITHUB_USER=my-name GITHUB_HMAC_SECRET=my-secret
$ git push heroku master
$ heroku open
```

## Webhooks

In order to use auto-comments, you need webhooks
on your repositories. Here are the commands to use:

* To create a hook for just one repository:
  `GITHUB_APP_DOMAIN=my-domain GITHUB_HMAC_SECRET=my-secret lein github create-hook USER REPO`
* To create hooks for all your original repositories (*caution: one API call per repository*):
  `GITHUB_APP_DOMAIN=my-domain GITHUB_HMAC_SECRET=my-secret lein github create-hook :all`
* To list your hooks (*caution: one API call per repository*):
  `lein github hooks`
* To list more about hooks for one repository:
  `lein github hooks USER REPO`
* To delete a hook for just one repository:
  `lein github delete-hook USER REPO ID`
* To delete a hook for just one repository (*caution: one API call per
  repository*):
  `lein github delete-hook :all`

## Configuration

The following environment variables allows you to configure what user
and issues the public can see:

* `$GITHUB_AUTH (required)` - This is your username:password basic auth
  [as described in github's docs](http://developer.github.com/v3/#authentication)
* `$GITHUB_USER (required)` - Your github username
* `$GITHUB_HMAC_SECRET (recommended)` - Secret key used when github
  posts to /webhook for issue state changes. If used, it should be
  be set on the app and when creating webhooks.
* `$GITHUB_ISSUE_REGEX` - A string that's interpreted as a regex to
  filter what repositories are viewable. This defaults to
  github.com/$GITHUB_USER. Since this app fetches issues across all orgs
  or repositories a user has access to, you can potentially allow
  users to see your repositories plus whatever other organization
  repositories you'd like e.g. github.com/(cldwalker|pedestal).
* `$GITHUB_HIDE_LABELS` - Given a comma delimited list of labels, it
  hides issues with those labels. Useful when labels indicate issues
  that you're not actively working on but have left open. If nothing
  is specified, the default is to hide issues with any labels.
* `$GITHUB_APP_DOMAIN` - This is required if you're using auto-commenting.
  This should be the full domain of your app and will be used mainly
  for link generation.
* `$GITHUB_HOOK_FORKS` - When set to anything i.e. "1", this includes
  forks for listing and creating webhooks.

## Who this is for

If you're a maintainer or organization who does any of the following:
* strives to respond to all their issues
* treats all their active repositories equally and runs through issues in the
  order they are created
* likes to leave issues open for others but has no intention of
  working on them (use labels and $GITHUB_HIDE_LABELS)
* likes to only see issues from a subset of all repositories (use
  $GITHUB_ISSUE_REGEX)

then this is for you.

## Credits
* Joshua Johnson for providing list styling in
  [this article](http://designshack.net/articles/css/5-simple-and-practical-css-list-styles-you-can-copy-and-paste/)
* @visibletrap for
  [hmac-sha1 code](https://gist.github.com/visibletrap/4571244)
* @Raynes for making
  [github's API fun and easy to use](https://github.com/Raynes/tentacles)
* @pedestal for [pedestal-service](https://github.com/pedestal/pedestal/tree/master/service)

## TODO
* Lein task to be run nightly for adding webhooks to new repositories
