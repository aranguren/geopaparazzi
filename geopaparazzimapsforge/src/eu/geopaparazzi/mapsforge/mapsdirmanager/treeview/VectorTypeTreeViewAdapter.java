package eu.geopaparazzi.mapsforge.mapsdirmanager.treeview;

import java.util.Set;

import eu.geopaparazzi.mapsforge.R;
import eu.geopaparazzi.mapsforge.mapsdirmanager.treeview.TreeNodeInfo;
import eu.geopaparazzi.mapsforge.mapsdirmanager.treeview.TreeStateManager;
import eu.geopaparazzi.spatialite.database.spatial.activities.PointsDataPropertiesActivity;
import eu.geopaparazzi.spatialite.database.spatial.activities.LinesDataPropertiesActivity;
import eu.geopaparazzi.spatialite.database.spatial.activities.PolygonsDataPropertiesActivity;
import eu.geopaparazzi.spatialite.database.spatial.core.GeometryType;
import eu.geopaparazzi.spatialite.util.SpatialiteLibraryConstants;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class VectorTypeTreeViewAdapter extends VectorDirectoryTreeViewAdapter
{
 public VectorTypeTreeViewAdapter(final VectorTreeViewList treeViewList_Vector,final Set<Long> selected_nodes,final TreeStateManager<Long> treeStateManager,final int numberOfLevels)
 {
  super(treeViewList_Vector, selected_nodes, treeStateManager, numberOfLevels);
 }
 @Override
 public LinearLayout updateView(final View view,final TreeNodeInfo<Long> node_info)
 {
  final LinearLayout viewLayout = (LinearLayout) view;
  final TextView descriptionView = (TextView) viewLayout.findViewById(R.id.vector_treeview_item_fields_short_text);
  final TextView typeView = (TextView) viewLayout.findViewById(R.id.vector_treeview_item_fields_type);
  // descriptionView.setText(getDescription(treeNodeInfo.getId()));
  // typeView.setText(Integer.toString(treeNodeInfo.getLevel()));
  descriptionView.setText(node_info.getShortDescription());
  ImageButton zoomtoButton = (ImageButton) viewLayout.findViewById(R.id.vector_treeview_item_zoomto);
  ImageButton propertiesButton = (ImageButton) viewLayout.findViewById(R.id.vector_treeview_item_properties);
  CheckBox enabledCheckBox = (CheckBox) viewLayout.findViewById(R.id.vector_treeview_item_enabled);
  if (node_info.isWithChildren())
  {
   typeView.setText("");
   zoomtoButton.setVisibility(View.INVISIBLE);
   propertiesButton.setVisibility(View.INVISIBLE);
   enabledCheckBox.setVisibility(View.INVISIBLE);
  }
  else
  {
   typeView.setText("["+node_info.getTypeText()+"]");
   enabledCheckBox.setChecked(node_info.getEnabled() != 0);
   zoomtoButton.setVisibility(View.VISIBLE);
   propertiesButton.setVisibility(View.VISIBLE);
   enabledCheckBox.setVisibility(View.VISIBLE);
   /*
   zoomtoButton.setOnClickListener(new View.OnClickListener()
   {
    public void onClick( View v )
    {
     double[] tableBounds = node_info.getPositionValues();
     Intent intent = getIntent();
     intent.putExtra(SpatialiteLibraryConstants.LATITUDE, tableBounds[5]);
     intent.putExtra(SpatialiteLibraryConstants.LONGITUDE, tableBounds[4]);
     this.activity.setResult(Activity.RESULT_OK, intent);
     this.activity.finish();
    }
   });
   propertiesButton.setOnClickListener(new View.OnClickListener()
   {
    public void onClick( View v )
    {
     Intent intent = null;
     GeometryType TYPE = GeometryType.forValue(node_info.getType());
     switch( TYPE )
     {
      case POLYGON_XY:
      case POLYGON_XYM:
      case POLYGON_XYZ:
      case POLYGON_XYZM:
      case MULTIPOLYGON_XY:
      case MULTIPOLYGON_XYM:
      case MULTIPOLYGON_XYZ:
      case MULTIPOLYGON_XYZM:
       intent = new Intent(this.activity, PolygonsDataPropertiesActivity.class);
      break;
      case POINT_XY:
      case POINT_XYM:
      case POINT_XYZ:
      case POINT_XYZM:
      case MULTIPOINT_XY:
      case MULTIPOINT_XYM:
      case MULTIPOINT_XYZ:
      case MULTIPOINT_XYZM:
        intent = new Intent(this.activity, PointsDataPropertiesActivity.class);
      break;
      case LINESTRING_XY:
      case LINESTRING_XYM:
      case LINESTRING_XYZ:
      case LINESTRING_XYZM:
      case MULTILINESTRING_XY:
      case MULTILINESTRING_XYM:
      case MULTILINESTRING_XYZ:
      case MULTILINESTRING_XYZM:
       intent = new Intent(this.activity, LinesDataPropertiesActivity.class);
      break;
      case GEOMETRYCOLLECTION_XY:
      case GEOMETRYCOLLECTION_XYM:
      case GEOMETRYCOLLECTION_XYZ:
      case GEOMETRYCOLLECTION_XYZM:
       intent = new Intent(this.activity, PointsDataPropertiesActivity.class);
      break;
     }
     intent.putExtra(SpatialiteLibraryConstants.PREFS_KEY_TEXT,node_info.getFileNamePath());
     startActivity(intent);
    }
   });
   * */
  }
  ImageView this_view = (ImageView) viewLayout.findViewById(R.id.vector_treeview_item_fields_options_button);
  return viewLayout;
 }
}
