# KartaSoloSlumber

A PaperMC plugin inspired by the OnePlayerSleep datapack.

Tired of waiting for everyone else to sleep? Now you can just get in bed to pass the night and clear those nasty thunderstorms! (as long as there are no objections)

This plugin is developed by **MinekartaStudio** and is compatible with **PaperMC 1.21**.

## Features

*   **One Player Sleep:** Only one player needs to sleep to pass the night.
*   **Configurable Messages:** Customize sleep, wakeup, and other messages.
*   **Random Messages:** Set multiple sleep messages with different chances.
*   **Hover and Click Events:** Messages can have hover text and click actions.
*   **Async Operations:** Sleep messages and randomization are run asynchronously to maintain performance.
*   **PlaceholderAPI Support:** Use placeholders in messages if you have PlaceholderAPI installed.
*   **Permissions:** Fine-grained control over who can use the plugin's features.

## Commands

*   `/sleep`: Shows the help menu.
*   `/sleep help`: Shows the help menu.
*   `/sleep reload`: Reloads the plugin's configuration files.
*   `/sleep test`: Tests a sleep message.
*   `/sleep wakeup`: Kicks a player from their bed.

## Permissions

*   `sleep.see`: Allows a player to see the help menu.
*   `sleep.reload`: Allows a player to reload the configuration.
*   `sleep.wakeup`: Allows a player to kick others from bed.
*   `sleep.bypass`: Prevents a player from being kicked from bed.
*   `sleep.test`: Allows a player to test sleep messages.
*   `sleep.ignore`: The plugin will ignore this player.
*   `sleep.global`: Allows a player to wake up players in other worlds.

## Configuration

The plugin comes with three configuration files:

*   `config.yml`: Main configuration file for the plugin.
*   `lang.yml`: Contains all the messages used by the plugin.
*   `messages.yml`: Define your custom sleep messages here.
