Example 3: Using a custom sites file
------------------------------------

__Working directory:__ `/path/to/nshmp-haz/etc/examples`

To compute hazard at more than one site, one may supply a comma-delimited (\*.csv) or [GeoJSON](http://geojson.org) (\*.geojson) formatted site data file instead:

```Shell
hazard ../peer/models/Set1-Case1  3-sites-file/sites.csv 3-sites-file/config.json
```

or

```Shell
hazard ../peer/models/Set1-Case1 3-sites-file/sites.geojson 3-sites-file/config.json
```

The [site specification](https://github.com/usgs/nshmp-haz/wiki/Sites) wiki page provides details on the two file formats. Note that with either format, if the name of a site is supplied, it will be included in the first column of any output curve files.

Note that both formats ([CSV](sites.csv) and [GeoJSON](sites.geojson)) are elegantly rendered by GitHub.

#### Next: [Example 4 – A simple hazard map](../4-hazard-map)