# Composer

Composer is a single-page application for easy creation and managing of geoserver maps.

## Requirements

Building Composer requires Node and npm (v3.x) be [installed](http://nodejs.org/). In addition to installing node and npm it is recommended that `bower` and `grunt-cli` be installed globally:

    npm install -g bower
    npm install -g grunt-cli

## Building

To install all project dependencies simply run

    npm install

To build all client side assets run

    grunt build

## Developing

For development it is most convenient to run the backend and front-end
separately.

See [Suite webapp Readme](https://github.com/boundlessgeo/suite/tree/master/geoserver/webapp#developing) for more details on setting up the backend.

After following the build steps above you can run the debug server:

    grunt start

By default the frontend server starts on port 8000. The server will watch for changes to JavaScript and CSS assets and perform a live reload without having to restart the debug server.

### GeoServer Proxy Settings

By default the frontend server will proxy for GeoServer at horizon.boundlessgeo.com. To change this create a file named `proxy.json` in the same directory as `gruntfile.js`. For example:

    {
       "host": "localhost",
       "port": 8080
    }

### Dependancy Management

Composer uses `npm` and `bower` for dependency management. Dependancies are tracked in `package.json` or `bower.json` respectively. 

`npm` is mostly used for build dependancies such as `bower` and `grunt`, and for dependancies not available through bower, such as `mapcfg` or `openlayers`.

`bower` is used for everything else, including all `angular` dependancies. Note that whenever a new version of angular is released, `bower.json` should be updated with the new angular version, otherwise npm install will fail upon dependancy resolution (This also blocks the suite jenkins build).

### Project Structure

The root of the composer project has a number of subdirectories:

* `app` - Composer codebase
* `vendor` - Local copies of unsupported or defunct js extensions.
* `bower_components` - Generated by `bower install` (by way of `npm install`). Includes all dependancies declared in `bower.json`.
* `node_modules` - Generated by `npm install`. Includes all dependancies declared in `package.json`.
* `build` - Generated by `grunt build`. Includes all build artifacts.

The main composer codebase is in `app`. Generally, all composer changes should be limited to this directory. The root level contains `index.html`, CSS variable declarations, and angular project configuration. Everything else is further divided into subdirectories:

* `/components` - Stand alone components that may be used in multiple places. Most of the more complex js implementations are in this folder or in `/core`. Other subfolders are generally limited to simple, UI-centric controllers.
  * `/alertpanel` - Modal dialog to show recent alert popovers
  * `/editor` - Style Editor.
    * `/olmap` - OL3 Map view for style editor page.
    * `/layerlist` - Layer list for map editor.
    * `/styleeditor` - Code-mirror styleeditor + YSLD hinter.
    * `/tools` - Editor Toolbar widgets (Save | Layers | Fullscreen | Colors | ...).
  * `/errorpanel` - Alert popover implementation.
  * `/featureinfopanel` - Attribute information panel, displayed when clicking on map feature on the OL3 map view.
  * `/grid` - ui-grid extensions/utilities.
  * `/import` - File/Database Data importer.
  * `/modalform` - Shared modal form components. Basic frames, Map/Layer/Workspace CRUD.
    * `/map` - Map New/Edit modals.
    * `/layer` - Layer New/Edit/Duplicate modals.
    * `/workspace` - Workspace New/Edit modals.
  * `/olexport` - OL3 JS export for maps page.
  * `/projfield` - Projection text field with back-end validation.
  * `/sidenav` - Side navigation bar (Workspaces | All Maps / All Layers | Recent).
  * `/topnav` - Top navigation bar (Home | Help / Recent Alerts / Admin).
* `/core` - Core functions - Authentication, Backend API access, Events.
  * `/login` - Login page and login modal.
* `/home` - Home page (Getting started + Recent).
* `/images` - Image resources.
* `/layers` - All Layers page.
* `/maps` - All Maps pages.
* `/workspaces` - Workspaces list and Map/Layer/Data tabs.
  * `/details` - Workspace Details. Map/Layer/Data tabs.
    * `/data` - Data view.
    * `/layers` - Layers view.
    * `/maps` - Maps view.
