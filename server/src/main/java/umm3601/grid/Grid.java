package umm3601.grid;
import java.util.Date;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})
public class Grid {
  @ObjectId @Id
  @SuppressWarnings({"memberName"})
  public String _id;
  public String roomID;
  public GridCell[][] grid;
  public String name;
  public Date lastSaved;
}
