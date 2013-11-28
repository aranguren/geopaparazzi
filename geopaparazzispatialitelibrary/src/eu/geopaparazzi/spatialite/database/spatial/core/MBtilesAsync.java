package eu.geopaparazzi.spatialite.database.spatial.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import eu.geopaparazzi.library.database.GPLog;
import eu.geopaparazzi.library.network.NetworkUtilities;
import eu.geopaparazzi.spatialite.database.spatial.SpatialiteContextHolder;
import eu.geopaparazzi.spatialite.database.spatial.core.mbtiles.MBTilesDroidSpitter;

// http://www.vogella.com/articles/AndroidBackgroundProcessing/article.html
// - CursorLoader
// mbtiles_Async download_tiles= new mbtiles_Async(this);
// download_tiles.execute("");
// download_tiles.cancel(true);
// executeOnExecutor(java.util.concurrent.Executor, Object[])
// RUNNING
class MBtilesAsync extends AsyncTask<MbtilesDatabaseHandler.AsyncTasks, String, Integer> {
    private MbtilesDatabaseHandler db_mbtiles;
    private List<MbtilesDatabaseHandler.AsyncTasks> async_parms = null;
    private HashMap<String, String> mbtiles_request_url = null;
    private String s_request_url_source = "";
    private int i_tile_server = 0; // if no 'SSS' is found, server logic will not be called
    private String s_request_type = ""; // 'fill','replace'
    private String s_request_bounds = "";
    private String s_request_bounds_url = "";
    private int i_request_zoom_min = 22; // will be set properly on construction
    private int i_request_zoom_max = 0; // will be set properly on construction
    private int i_url_zoom_min = 22; // will be set properly on construction
    private int i_url_zoom_max = 0; // will be set properly on construction
    private String s_request_y_type = "osm"; // 0=osm ; 1=tms ; 2=wms
    private List<Integer> zoom_levels = null;
    private double[] request_bounds = null;
    private String s_message = "";
    private int i_http_code = -1;
    private int i_http_not_usable = 0;
    private int i_http_bad_requests = 0;
    private String s_http_message = "";
    private String s_http_result = "";
    // -----------------------------------------------
    /**
      * Constructor
      * - Base values will be take from MbtilesDatabaseHandler:
      * -- an extreame amout of sanit-checks will be made
      * --- Goal: attemt to avoid any server spins when rquesting tiles
      * @param db_mbtiles MbtilesDatabaseHandler
      * @return itsself
     */
    public MBtilesAsync(MbtilesDatabaseHandler db_mbtiles ) {
        this.db_mbtiles = db_mbtiles;
        this.async_parms = this.db_mbtiles.getAsyncParms();
        for( int i = 0; i < async_parms.size(); i++ ) {
            s_message += this.async_parms.get(i);
            if (i < (async_parms.size() - 1))
                s_message += ",";
        }
        if (!this.db_mbtiles.s_request_type.equals("")) {
            this.s_request_type = this.db_mbtiles.s_request_type;
            if (!s_message.equals(""))
                s_message += ",";
            s_message += this.s_request_type;
        }
        this.s_request_url_source = this.db_mbtiles.s_request_url_source;
        if (!this.s_request_url_source.equals("")) {
            if (check_request_bounds(this.db_mbtiles.s_request_bounds, this.db_mbtiles.s_request_bounds_url) > 0) { // invalid
                this.s_request_url_source = "";
            } else { // correct
                int indexOfS = this.s_request_url_source.indexOf("SSS");
                if (indexOfS != -1) {
                    this.i_tile_server = 1; // Server logic will not be called [1,2]
                }
                if ((this.db_mbtiles.s_request_y_type.equals("tms")) || (this.db_mbtiles.s_request_y_type.equals("wms"))) {
                    this.s_request_y_type = this.db_mbtiles.s_request_y_type;
                }
            }
        }
        if (!this.s_request_bounds.equals("")) { // only if valid
            if ((!this.db_mbtiles.s_request_zoom_levels.equals("")) && (!this.db_mbtiles.s_request_zoom_levels_url.equals(""))) {
                create_zoom_levels(this.db_mbtiles.s_request_zoom_levels, this.db_mbtiles.s_request_zoom_levels_url);
            }
        }
    }
    protected void onPreExecute() {
        GPLog.androidLog(-1, "mbtiles_Async.tasks[" + db_mbtiles.getName() + "][" + s_message + "]");
    }
    protected Integer doInBackground( MbtilesDatabaseHandler.AsyncTasks... async_values ) {
        int i_rc = 0;
        if (async_values[0] != MbtilesDatabaseHandler.AsyncTasks.ASYNC_PARMS) {
            this.async_parms.clear();
            for( int i = 0; i < async_values.length; i++ ) {
                this.async_parms.add(async_values[i]);
            }
        }
        GPLog.androidLog(-1, "mbtiles_Async.On doInBackground[" + db_mbtiles.getName() + "]");
        for( int i = 0; i < this.async_parms.size(); i++ ) {
            if (isCancelled()) {
                i_rc = 1077;
                s_message = "-W-> mbtiles_Async.On doInBackground[" + db_mbtiles.getName() + "] rc=" + i_rc;
                break;
            }
            MbtilesDatabaseHandler.AsyncTasks async_task = this.async_parms.get(i);
            switch( async_task ) {
            case ANALYZE_VACUUM: { // implemented, but not tested
                i_rc = on_analyse_vacuum();
            }
                break;
            case UPDATE_BOUNDS: { // extensive checking of bounds with update of mbtiles.metadata
                                  // table
                i_rc = on_update_bounds();
            }
                break;
            case REQUEST_URL: { // retrieve tiles found in 'request_url'
                int i_test_downloads = 0;
                // i_test_downloads=1;
                if (i_test_downloads == 0) {
                    i_rc = on_request_url();
                } else {
                    try {
                        Bitmap bm_test = null;
                        // this returns a valid image
                        String s_tile_url = "http://fbinter.stadt-berlin.de/fb/wms/senstadt/k_luftbild2011_20?LAYERS=0&FORMAT=image/jpeg&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=visual&SRS=EPSG:4326&BBOX=13.293457031249988,52.562995039558004,13.315429687500005,52.57634993749886&WIDTH=256&HEIGHT=256";
                        bm_test = on_download_tile_http(s_tile_url);
                        // this returns blank [area not supported]
                        s_tile_url = "http://fbinter.stadt-berlin.de/fb/wms/senstadt/k_luftbild2011_20?LAYERS=0&FORMAT=image/jpeg&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=visual&SRS=EPSG:4326&BBOX=13.20556640624999,52.6430634366589,13.227539062500007,52.656393941988014&WIDTH=256&HEIGHT=256";
                        bm_test = on_download_tile_http(s_tile_url);
                        // this returns an error [<ServiceException code="LayerNotDefined">theme
                        // k_luftbild1938@senstadt access denied</ServiceException>]
                        s_tile_url = "http://fbinter.stadt-berlin.de/fb/wms/senstadt/k_luftbild1938?LAYERS=0&FORMAT=image/jpeg&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=visual&SRS=EPSG:4326&BBOX=13.20556640624999,52.6430634366589,13.227539062500007,52.656393941988014&WIDTH=256&HEIGHT=256";
                        bm_test = on_download_tile_http(s_tile_url);
                    } catch (Exception e) {
                    }
                }
            }
                break;
            case REQUEST_CREATE: { // create and fill 'request_url' with tile-requests
                if (zoom_levels != null) {
                    i_rc = on_request_create();
                } else {
                    i_rc = 2000;
                }
            }
                break;
            case REQUEST_DELETE: { // todo: delete an area of the db

            }
                break;
            case REQUEST_DROP: { // Drop the request table
                i_rc = db_mbtiles.get_request_url_count(MBTilesDroidSpitter.i_request_url_count_drop);
            }
                break;
            case REQUEST_PING: { // todo: check if internet is really working [i.e. a valid image
                                 // can be downloaded]

            }
                break;
            }
        }
        s_message = "-I-> mbtiles_Async.On doInBackground[" + db_mbtiles.getName() + "] all tasks compleated rc=" + i_rc;
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * House-keeping tasks for Database
      * The ANALYZE command gathers statistics about tables and indices
      * The VACUUM command rebuilds the entire database.
      * - A VACUUM will fail if there is an open transaction, or if there are one or more active SQL statements when it is run.
      * @return 0=correct ; 1=ANALYSE has failed ; 2=VACUUM has failed
      */
    private int on_analyse_vacuum() {
        return db_mbtiles.on_analyse_vacuum();
    }
    // -----------------------------------------------
    /**
      * will retrieve the list of requested tile-images
      * - retrieves list from 'request_url' table [if any]
      * - for each request 'on_request_tile_id_url' will be called
      * -- isCancelled() is called before each request
      * @return i_rc [ 0: task compleated; 1=task interupted]
     */
    private int on_update_bounds() {
        int i_rc = 0;
        int i_reload_metadata = 1;
        db_mbtiles.update_bounds(i_reload_metadata);
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * will retrieve the list of requested tile-images
      * - retrieves list from 'request_url' table [if any]
      * - for each request 'on_request_tile_id_url' will be called
      * -- isCancelled() is called before each request
      * @return i_rc [ 0: task compleated; 1=task interupted]
     */
    private int on_request_url() {
        int i_rc = 0;
        mbtiles_request_url = db_mbtiles.retrieve_request_url();
        int i_count_tiles_total = mbtiles_request_url.size();
        int i_count_tiles_count = 0;
        int i_count_tiles_left = 0;
        int i_count_rest = 1;
        if (i_count_tiles_total > 0) { // avoid divide by zero error
            i_count_rest = i_count_tiles_total / 20; // 100=1% ; 80=1.25% ; 40=2.5% ; 20=5% ; 10=10%
                                                     // ; 5=20% ; 1=100%
            if (i_count_tiles_total > 10000) {
                i_count_rest = i_count_tiles_total / 100; // 100=1% ;
            } else {
                if (i_count_tiles_total > 5000) {
                    i_count_rest = i_count_tiles_total / 50; // 50=2% ;
                } else {
                    if (i_count_tiles_total > 2500) {
                        i_count_rest = i_count_tiles_total / 25; // 25=2% ;
                    }
                }
            }
            if (i_count_rest < 1)
                i_count_rest = 1;
        }
        s_message = "-I-> on_request_url[" + s_request_type + "][" + db_mbtiles.getName() + "]: mbtiles_request_url["
                + i_count_tiles_total + "] ";
        publishProgress(s_message);
        Context context = SpatialiteContextHolder.INSTANCE.getContext();
        boolean networkAvailable = NetworkUtilities.isNetworkAvailable(context);
        for( Map.Entry<String, String> request_url : mbtiles_request_url.entrySet() ) {
            if (i_http_not_usable > 0) {
                i_rc = 3775;
                s_message = "-W-> on_request_url[" + s_http_result + "][" + db_mbtiles.getName() + "]: mbtiles_request_url["
                        + i_count_tiles_total + "] rc=" + i_rc;
                publishProgress(s_message);
                return i_rc;
            }
            if (!networkAvailable) {
                i_rc = 3776;
                s_http_result = "No Internet Connection";
                s_message = "-W-> on_request_url[" + s_http_result + "][" + db_mbtiles.getName() + "]: mbtiles_request_url["
                        + i_count_tiles_total + "] rc=" + i_rc;
                publishProgress(s_message);
                return i_rc;
            }
            if (isCancelled()) {
                i_rc = 3777;
                s_message = "-W-> on_request_url[" + s_request_type + "][" + db_mbtiles.getName() + "]: mbtiles_request_url["
                        + i_count_tiles_total + "] rc=" + i_rc;
                return i_rc;
            }
            String s_tile_id = request_url.getKey();
            String s_tile_url = request_url.getValue();
            i_rc = on_request_tile_id_url(s_tile_id, s_tile_url);
            i_count_tiles_count++;
            i_count_tiles_left = i_count_tiles_total - i_count_tiles_count;
            if ((i_count_tiles_count % i_count_rest) == 0) {
                double d_procent = (double) i_count_tiles_left;
                d_procent = 100 - ((d_procent / i_count_tiles_total) * 100);
                s_message = "-I-> on_request_url[" + db_mbtiles.getName() + "][" + s_request_type + "]: tile_id[" + s_tile_id
                        + "] retrieved[" + i_count_tiles_count + "] [" + d_procent + " %] open[" + i_count_tiles_left
                        + "] total[" + i_count_tiles_total + "]";
                publishProgress(s_message);
            }
        }
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * will retrieve  requested tile-image [on_download_tile]
      * - will store each retieved bitmap in the mbtiles-file [db_mbtiles.insertBitmapTile]
      * - will remove the request [db_mbtiles.delete_request_url]
      * @param s_tile_id tile_id to use
      * @param s_tile_url full url to retrieve tile with
      * @return i_rc [ 0: image retrieved and stored; 101=image not retrieved ; 100=invalid mbtiles ; otherwise some storing error]
     */
    private int on_request_tile_id_url( String s_tile_id, String s_tile_url ) {
        int i_rc = 0;
        int[] zxy_osm_tms = MBTilesDroidSpitter.get_zxy_from_tile_id(s_tile_id);
        if ((zxy_osm_tms != null) && (zxy_osm_tms.length == 4)) {
            int i_zoom = zxy_osm_tms[0];
            int i_tile_x = zxy_osm_tms[1];
            int i_tile_y_osm = zxy_osm_tms[2];
            int i_tile_y_tms = zxy_osm_tms[3];
            try {
                Bitmap tile_bitmap = on_download_tile_http(s_tile_url);
                if (tile_bitmap != null) {
                    int i_force_unique = 0;
                    int i_force_bounds = 0; // after each insert, update bounds and min/max zoom
                                            // levels
                    i_rc = db_mbtiles.insertBitmapTile(i_tile_x, i_tile_y_osm, i_zoom, tile_bitmap, i_force_unique);
                    if (i_rc == 0) {
                        i_http_bad_requests = 0;
                        db_mbtiles.delete_request_url(s_tile_id);
                    }
                } else {
                    if ((i_http_bad_requests > 10) && (i_http_not_usable == 0)) { // provider is not
                                                                                  // sending
                                                                                  // anything
                        s_http_result = "Internet Connection: recieved [" + i_http_bad_requests + "] bad requests";
                        i_http_not_usable = 1;
                    }
                }
            } catch (Throwable t) {
                i_rc = 2;
                GPLog.androidLog(4, "mbtiles_Async on_request_tile_id_url[" + db_mbtiles.getName() + "][" + s_tile_id + "] ["
                        + s_tile_url + "] rc=" + i_rc, t);
            }
        } else {
            i_rc = 1;
        }
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * will retrieve  requested tile-image [s_tile_url]
      * - goal is to determin whether we have a fake connection or a non-publich server
      * -- provider will NOT return results - only requests for money - therefore connection not usable
      * --- 302 HTTP_MOVED_TEMP
      * -- even with 'access denied', HTTP_OK is returned
      * Goal is to avoid unneeded / invalid requests assuming that a valid http-code is returned
      * @param s_tile_url full url to retrieve tile with
      * @return i_rc [ 0: image retrieved and stored; 101=image not retrieved ; 100=invalid mbtiles ; otherwise some storing error]
     */
    private Bitmap on_download_tile_http( String s_tile_url ) throws Exception {
        Bitmap tile_bitmap = null;
        i_http_code = -1;
        int i_image_null = 1;
        int i_content_length = 0;
        try {
            URL this_url = new URL(s_tile_url);
            InputStream input_stream = null;
            HttpURLConnection this_http = null;
            try {
                this_http = (HttpURLConnection) this_url.openConnection();
                this_http.setDoInput(true);
                i_content_length = this_http.getContentLength(); // the size of the gziped value
                                                                 // returned
                input_stream = this_http.getInputStream();
                tile_bitmap = BitmapFactory.decodeStream(input_stream);
                input_stream.close();
                if (tile_bitmap == null) { // possible 'access denied' - not a public server -should
                                           // be considered an invalid server [returns HTTP_OK]
                    if (i_content_length > 0) { // is probely an error text similer to:
                                                // <ServiceException code="LayerNotDefined">theme
                                                // k_luftbild1938@senstadt access
                                                // denied</ServiceException>
                    }
                } else {
                    i_image_null = 0;
                }
            } catch (IOException e) {
                s_message = "mbtiles_Async.on_download_tile: http_code[" + this_http.getResponseCode() + "] ["
                        + this_http.getResponseMessage() + "]" + e.getMessage();
                GPLog.androidLog(4, s_message, e);
                tile_bitmap = null;
            } finally { // will set values, depending on values to determin if this task should be
                        // aborted
                get_http_result(0, this_http.getResponseCode(), this_http.getResponseMessage(), i_content_length, i_image_null);
            }
        } catch (Throwable t) {
        }
        return tile_bitmap;
    }
    // -----------------------------------------------
    /**
      * will interpate the result and attempt to deside if this is a usable Internet connection
      * - goal is to determin whether we have a fake connection or a non-publich server
      * -- provider will NOT return results - only requests for money - therefore connection not usable
      * --- 302: HTTP_MOVED_TEMP
      * -- even with 'access denied', HTTP_OK is returned, but with an error text being sent (i_content_length != -1)
      * @param i_parm reservered
      * @param i_http_code http code recieved
      * @param s_http_code http text recieved
      * @param i_content_length gziped length of data recieved [images always '-1']
      * @param i_image_null 0= no valid imageg recieved ; 1= valid image recieved
      * @return i_http_code
     */
    private int get_http_result( int i_parm, int i_http_code, String s_http_message, int i_content_length, int i_image_null ) {
        this.i_http_code = i_http_code;
        this.s_http_message = s_http_message;
        this.s_http_result = "[" + this.i_http_code + "][" + this.s_http_message + "]";
        switch( this.i_http_code ) {
        case HttpURLConnection.HTTP_OK: { // this is the desired result
            s_message = "mbtiles_Async.get_http_result: i_image_null[" + i_image_null + "] content_length[" + i_content_length
                    + "] [" + s_http_result + "]";
            if (i_image_null < 1) {
                i_http_not_usable = 0; // is usable
                i_http_bad_requests = 0;
            } else { // possible 'access denied' - not a public server -should be considered an
                     // invalid server [returns never the less: HTTP_OK]
                     // after 10 attempts, will abort
                i_http_bad_requests++;
                // i_content_length=4;
            }
            // GPLog.androidLog(i_content_length,"mbtiles_Async.get_http_result: "+s_message);
        }
            break;
        case HttpURLConnection.HTTP_MOVED_TEMP: { // this can be a connection that returns no
                                                  // results until you activate an account
            s_http_result = "Internet Connection: recieved [" + this.s_http_result + "] - aborting";
            i_http_not_usable = 1;
        }
            break;
        default:
            GPLog.androidLog(-1, "mbtiles_Async.get_http_result: " + s_http_result);
            break;
        }
        return this.i_http_code;
    }
    private Bitmap on_download_tile( String s_tile_url ) throws Exception {
        Bitmap tile_bitmap = null;
        try {
            URL this_url = new URL(s_tile_url);
            InputStream input_stream = null;
            HttpURLConnection this_http = null;
            try {
                input_stream = this_url.openStream();
                tile_bitmap = BitmapFactory.decodeStream(input_stream);
                input_stream.close();
            } catch (IOException e) {
                String s_message = e.getMessage();
                tile_bitmap = null;
            }
        } catch (Throwable t) {
        }
        return tile_bitmap;
    }
    // -----------------------------------------------
    /**
      * Create list of 'request_url
      * - Assumtions:
      * -- we have a valid bounds
      * -- we have valid zoom_levels
      * -- we have a valid request url
      * -- '5-3,7-8,6'
      * -- order is not important, mixing up min/max will be corrected
      * -- only unique values will be stored
      * - valid zoom-levels: 0-22
      * - result will sorted from min to max,West to East ; North to South
      * @return zoom_levels.size() [ amount of valid,sorted zoom_levels found]
     */
    private int on_request_create() {
        int i_rc = 0;
        int i_count_tiles_total = 0;
        int i_count_tiles_level = 0;
        // retrieve list of existing requests (if any)
        HashMap<String, String> prev_request_url = db_mbtiles.retrieve_request_url();
        int i_prev_request = prev_request_url.size();
        mbtiles_request_url = new LinkedHashMap<String, String>();
        s_message = "-I-> on_request_create[" + s_request_type + "][" + db_mbtiles.getName() + "]: zoom_levels["
                + zoom_levels.size() + "]";
        publishProgress(s_message);
        for( int i = 0; i < zoom_levels.size(); i++ ) { // for all selected zoom levels
            int i_zoom_level = zoom_levels.get(i);
            if (isCancelled()) {
                i_rc = 2777;
                s_message = "-W-> on_request_create[" + db_mbtiles.getName() + "]: zoom_level[" + i_zoom_level + "] tiles["
                        + i_count_tiles_level + "] total[" + i_count_tiles_total + "] rc=" + i_rc;
                return i_rc;
            }
            // retrieve list of missing[fill] or compleate list[replace] of tiles to retrieve
            List<String> list_tile_id = db_mbtiles.build_request_list(request_bounds, i_zoom_level, s_request_type);
            for( int j = 0; j < list_tile_id.size(); j++ ) {
                if (isCancelled()) {
                    i_rc = 2778;
                    s_message = "-W-> on_request_create[" + db_mbtiles.getName() + "]: zoom_level[" + i_zoom_level + "] tiles["
                            + i_count_tiles_level + "] total[" + i_count_tiles_total + "] rc=" + i_rc;
                    return i_rc;
                }
                String s_tile_id = list_tile_id.get(j);
                if (i_prev_request > 0) { // this function may have run mutiple times, avoid
                                          // allready existing requests ['load' not removed]
                    if (prev_request_url.containsKey(s_tile_id))
                        s_tile_id = "";
                }
                if (!s_tile_id.equals("")) { // Avoid error: code 19 constraint failed when
                                             // inserting image more than once
                                             // for each tile send tile_id[from which to position
                                             // will be calculated[wms] or the tile-numbers set]
                                             // - these values be set in the given url
                                             // -- the tile_id and created url will be added to
                                             // 'mbtiles_request_url'
                    on_request_create_url(s_tile_id, s_request_url_source);
                }
            }
            i_count_tiles_level = list_tile_id.size(); // even if not again save, the amount as
                                                       // information
            list_tile_id.clear();
            // save 'mbtiles_request_url' to the database
            i_count_tiles_total = db_mbtiles.insert_list_request_url(mbtiles_request_url);
            // clear 'mbtiles_request_url' that have been stored
            mbtiles_request_url.clear();
            s_message = "-I-> on_request_create[" + db_mbtiles.getName() + "]: zoom_level[" + i_zoom_level + "] tiles["
                    + i_count_tiles_level + "] total[" + i_count_tiles_total + "]";
            publishProgress(s_message);
        }
        s_message = "-I-> on_request_create[" + s_request_type + "][" + db_mbtiles.getName() + "]:  requested tiles["
                + i_count_tiles_total + "] have been saved.";
        publishProgress(s_message);
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * Parse s_request_bounds string
      * - Sample of supported formats:
      * -- '17'
      * -- '15-17'
      * -- '2,5,9-10,12'
      * -- '5-3,7-8,6'
      * -- order is not important, mixing up min/max will be corrected
      * -- only unique values will be stored
      * - valid zoom-levels: 0-22
      * - result will sorted from min to max
      * @param s_zoom_levels list of zoom levels
      * @return zoom_levels.size() [ amount of valid,sorted zoom_levels found]
     */
    private int check_request_bounds( String s_request_bounds, String s_request_bounds_url ) {
        int i_rc = 0;
        if ((!s_request_bounds.equals("")) || (!s_request_bounds_url.equals(""))) {
            double[] url_bounds = new double[]{0.0, 0.0, 0.0, 0.0};
            double[] test_bounds = new double[]{0.0, 0.0, 0.0, 0.0};
            String[] sa_string = null;
            int indexOfS = s_request_bounds.indexOf(",");
            if (indexOfS != -1) {
                sa_string = s_request_bounds.split(",");
            } else {
                sa_string = s_request_bounds.split("\\s+");
            }
            if (sa_string.length == 4) {
                try {
                    test_bounds[0] = Double.parseDouble(sa_string[0]);
                    test_bounds[1] = Double.parseDouble(sa_string[1]);
                    test_bounds[2] = Double.parseDouble(sa_string[2]);
                    test_bounds[3] = Double.parseDouble(sa_string[3]);
                } catch (NumberFormatException e) {
                    i_rc = 3;
                    return i_rc;
                }
            } else {
                i_rc = 2;
                return i_rc;
            }
            indexOfS = s_request_bounds_url.indexOf(",");
            if (indexOfS != -1) {
                sa_string = s_request_bounds_url.split(",");
            } else {
                sa_string = s_request_bounds_url.split("\\s+");
            }
            if (sa_string.length == 4) {
                try {
                    url_bounds[0] = Double.parseDouble(sa_string[0]);
                    url_bounds[1] = Double.parseDouble(sa_string[1]);
                    url_bounds[2] = Double.parseDouble(sa_string[2]);
                    url_bounds[3] = Double.parseDouble(sa_string[3]);
                } catch (NumberFormatException e) {
                    i_rc = 5;
                    return i_rc;
                }
            } else {
                i_rc = 4;
                return i_rc;
            }
            if (test_bounds[0] < url_bounds[0])
                test_bounds[0] = url_bounds[0];
            if (test_bounds[0] > url_bounds[2])
                test_bounds[0] = url_bounds[2];

            if (test_bounds[1] < url_bounds[1])
                test_bounds[1] = url_bounds[1];
            if (test_bounds[1] > url_bounds[3])
                test_bounds[1] = url_bounds[3];

            if (test_bounds[2] < url_bounds[0])
                test_bounds[2] = url_bounds[0];
            if (test_bounds[2] > url_bounds[2])
                test_bounds[2] = url_bounds[2];

            if (test_bounds[3] < url_bounds[1])
                test_bounds[3] = url_bounds[1];
            if (test_bounds[3] > url_bounds[3])
                test_bounds[3] = url_bounds[3];
            if ((test_bounds[0] == test_bounds[2]) || (test_bounds[1] == test_bounds[3])) {
                i_rc = 5;
                return i_rc;
            }
            // end of sanity checks
            this.request_bounds = new double[]{test_bounds[0], test_bounds[1], test_bounds[2], test_bounds[3]};
            this.s_request_bounds = s_request_bounds;
            this.s_request_bounds_url = s_request_bounds_url;
        } else {
            i_rc = 1;
        }
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * Parse zoom-level string
      * - Sample of supported formats:
      * -- '17'
      * -- '15-17'
      * -- '2,5,9-10,12'
      * -- '5-3,7-8,6'
      * -- order is not important, mixing up min/max will be corrected
      * -- only unique values will be stored
      * - valid zoom-levels: 0-22
      * - result will sorted from min to max
      * @param s_zoom_levels list of zoom levels
      * @return zoom_levels.size() [ amount of valid,sorted zoom_levels found]
     */
    private int create_zoom_levels( String s_zoom_levels, String s_zoom_levels_url ) {
        zoom_levels = new ArrayList<Integer>();
        if (add_zoom_from_to(s_zoom_levels_url, 0) != 0)
            return zoom_levels.size();
        int indexOfS = s_zoom_levels.indexOf(",");
        int i_zoom_level = 0;
        if (indexOfS != -1) {
            String[] sa_string = s_zoom_levels.split(",");
            for( int i = 0; i < sa_string.length; i++ ) {
                String s_zoom_level = sa_string[i];
                indexOfS = s_zoom_level.indexOf("-");
                if (indexOfS != -1) {
                    if (add_zoom_from_to(s_zoom_levels, 1) != 0)
                        return zoom_levels.size();
                } else {
                    try {
                        i_zoom_level = Integer.parseInt(s_zoom_levels);
                    } catch (NumberFormatException e) {
                        zoom_levels.clear();
                        return zoom_levels.size();
                    }
                    if (!zoom_levels.contains(i_zoom_level)) {
                        if ((i_zoom_level >= this.i_url_zoom_min) && (i_zoom_level <= this.i_url_zoom_max)) {
                            if (i_zoom_level < this.i_request_zoom_min)
                                this.i_request_zoom_min = i_zoom_level;
                            if (i_zoom_level > this.i_request_zoom_max)
                                this.i_request_zoom_max = i_zoom_level;
                            zoom_levels.add(i_zoom_level);
                        }
                    }
                }
            }
        } else {
            indexOfS = s_zoom_levels.indexOf("-");
            if (indexOfS != -1) {
                if (add_zoom_from_to(s_zoom_levels, 1) != 0)
                    return zoom_levels.size();
            } else {
                try {
                    i_zoom_level = Integer.parseInt(s_zoom_levels);
                } catch (NumberFormatException e) {
                    zoom_levels.clear();
                    return zoom_levels.size();
                }
                if (!zoom_levels.contains(i_zoom_level)) {
                    if ((i_zoom_level >= this.i_url_zoom_min) && (i_zoom_level <= this.i_url_zoom_max)) {
                        if (i_zoom_level < this.i_request_zoom_min)
                            this.i_request_zoom_min = i_zoom_level;
                        if (i_zoom_level > this.i_request_zoom_max)
                            this.i_request_zoom_max = i_zoom_level;
                        zoom_levels.add(i_zoom_level);
                    }
                }
            }
        }
        Collections.sort(zoom_levels);
        return zoom_levels.size();
    }
    // -----------------------------------------------
    /**
       * Parse zoom-level string [with from_to]
       * - Sample of supported formats:
       * -- '15-17'
       * -- order is not important, mixing up min/max will be corrected
       * -- only unique values will be stored
       * - valid zoom-levels: 0-22
       * @param s_zoom_levels list of zoom levels
       * @return i_rc [ correct ; 1=no '-' found]
      */
    private int add_zoom_from_to( String s_zoom_levels, int i_parm ) {
        int i_rc = 1;
        int indexOfS = s_zoom_levels.indexOf("-");
        int i_request_zoom_min = 0;
        int i_request_zoom_max = 0;
        if (indexOfS != -1) {
            String[] sa_string = s_zoom_levels.split("-");
            if (sa_string.length == 2) {
                try {
                    i_request_zoom_min = Integer.parseInt(sa_string[0]);
                    i_request_zoom_max = Integer.parseInt(sa_string[1]);
                } catch (NumberFormatException e) {
                    zoom_levels.clear();
                    return i_rc;
                }
                if (i_request_zoom_min > i_request_zoom_max) {
                    indexOfS = i_request_zoom_min;
                    i_request_zoom_max = i_request_zoom_min;
                    i_request_zoom_min = indexOfS;
                }
                if (i_parm == 0) {
                    if ((i_request_zoom_max >= 0) && (i_request_zoom_max <= 22)) {
                        if ((i_request_zoom_min >= 0) && (i_request_zoom_min <= 22)) {
                            this.i_url_zoom_min = i_request_zoom_min; // will be set properly on
                                                                      // construction
                            this.i_url_zoom_max = i_request_zoom_max; // will be set properly on
                                                                      // construction
                            return 0;
                        }
                    }
                    return i_rc;
                }
                for( int i_zoom_level = i_request_zoom_min; i_zoom_level <= i_request_zoom_max; i_zoom_level++ ) {
                    if (!zoom_levels.contains(i_zoom_level)) {
                        if ((i_zoom_level >= this.i_url_zoom_min) && (i_zoom_level <= this.i_url_zoom_max)) {
                            if (i_zoom_level < this.i_request_zoom_min)
                                this.i_request_zoom_min = i_zoom_level;
                            if (i_zoom_level > this.i_request_zoom_max)
                                this.i_request_zoom_max = i_zoom_level;
                            zoom_levels.add(i_zoom_level);
                            i_rc = 0;
                        }
                    }
                }
            }
        }
        return i_rc;
    }
    // -----------------------------------------------
    /**
      * will fill source_url [with placeholder] with valid values to retrieve requested tile-image
      * - adds result to 'mbtiles_request_url'
      * @param s_tile_id tile_id to use
      * @param s_url_source source url with placeholders for ZZZ,XXX,YYY and SSS
      * @return i_rc [ -1: s_tile_id incorrectly formatted; 0=valid s_tile_id and s_tile_url were added to the list ; ]
     */
    private int on_request_create_url( String s_tile_id, String s_url_source ) {
        int i_rc = 0;
        int indexOfZ = s_url_source.indexOf("ZZZ");
        int[] zxy_osm_tms = MBTilesDroidSpitter.get_zxy_from_tile_id(s_tile_id);
        if ((zxy_osm_tms != null) && (zxy_osm_tms.length == 4)) {
            int i_z = zxy_osm_tms[0];
            int i_x = zxy_osm_tms[1];
            int i_y_osm = zxy_osm_tms[2];
            int i_y_tms = zxy_osm_tms[3];
            int i_y = i_y_osm;
            if (s_request_y_type.equals("tms")) {
                i_y = i_y_tms;
            }
            String s_tile_url = s_url_source;
            if (i_tile_server > 0) { // ['http://otileSSS.mqcdn.com/'] replace
                                     // 'http://otile1.mqcdn.com/' with ''http://otile2.mqcdn.com/'
                s_tile_url = s_tile_url.replaceFirst("SSS", String.valueOf(i_tile_server++)); //$NON-NLS-1$
                if (i_tile_server > 2)
                    i_tile_server = 1;
            }
            if (indexOfZ != -1) { // tile-server: replace ZZZ,XXX,YYY
                s_tile_url = s_tile_url.replaceFirst("ZZZ", String.valueOf(i_z)); //$NON-NLS-1$
                s_tile_url = s_tile_url.replaceFirst("XXX", String.valueOf(i_x)); //$NON-NLS-1$
                s_tile_url = s_tile_url.replaceFirst("YYY", String.valueOf(i_y)); //$NON-NLS-1$
            } else { // wms_server
                double[] tileBounds = MBTilesDroidSpitter.tileLatLonBounds(i_x, i_y_osm, i_z, 256);
                s_tile_url = s_tile_url.replaceFirst("XXX", String.valueOf(tileBounds[0])); //$NON-NLS-1$
                s_tile_url = s_tile_url.replaceFirst("YYY", String.valueOf(tileBounds[1])); //$NON-NLS-1$
                s_tile_url = s_tile_url.replaceFirst("XXX", String.valueOf(tileBounds[2])); //$NON-NLS-1$
                s_tile_url = s_tile_url.replaceFirst("YYY", String.valueOf(tileBounds[3])); //$NON-NLS-1$
            }
            mbtiles_request_url.put(s_tile_id, s_tile_url);
        } else {
            i_rc = -1;
        }
        // GPLog.androidLog(4,"on_request_create_url["+s_tile_id+"] rc="+i_rc);
        return i_rc;
    }
    protected void onProgressUpdate( String... a ) {
        GPLog.androidLog(-1, "mbtiles_Async.[" + a[0] + "]");
    }
    protected void onPostExecute( Integer i_rc ) {
        GPLog.androidLog(-1, "mbtiles_Async.onPostExecute[" + i_rc + "] [" + s_message + "]");
    }
}