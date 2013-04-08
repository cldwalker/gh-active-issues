## Description

This is an app to let users know what number they are in my list of
ongoing github issues. Unfortunately, github doesn't provide a public view of a user's
[issues dashboard](https://github.com/dashboard/issues/repos). This
public and *curated* view of issues is an attempt to make users
understand why they are waiting.

My current app [lives on heroku](http://gh-waiting-room.herokuapp.com/).

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

## Running the App

1. Start the application: `lein run`
2. Go to [localhost:8080](http://localhost:8080/).
3. The first time the home page loads, it takes awhile since it's
   fetching the issues. Issues are saved in memory so subsequent reloads
   are instant.

## Logging Configuration

To configure logging see config/logback.xml. By default, the app logs to stdout and logs/.
To learn more about configuring Logback, read its
[documentation](http://logback.qos.ch/documentation.html).

## Credits
* Joshua Johnson for providing list styling in
  [this article](http://designshack.net/articles/css/5-simple-and-practical-css-list-styles-you-can-copy-and-paste/)
  
## TODO
* Listen on issue events and post a link to the user to let them
know where they are in the waiting room.
