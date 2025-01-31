package umm3601.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static com.mongodb.client.model.Filters.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import io.javalin.validation.BodyValidator;

@SuppressWarnings({ "MagicNumber" })
public class GridControllerSpec {
  private static MongoClient mongoClient;
  private static MongoDatabase db;
  private static JavalinJackson javalinJackson = new JavalinJackson();
  private ObjectId gridId;

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<List<Grid>> gridListCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  @Captor
  private ArgumentCaptor<Grid> gridCaptor;

  private GridController gridController;

  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    MockitoAnnotations.openMocks(this);
    MongoCollection<Document> gridDocuments = db.getCollection("grids");
    gridDocuments.drop();
    List<Document> testGrids = new ArrayList<>();
    testGrids.add(new Document().append("roomID", "testRoomID").append("grid", new ArrayList<>()));
    gridDocuments.insertMany(testGrids);
    gridId = new ObjectId();
    Document testGridWithId = new Document()
        .append("_id", gridId)
        .append("roomID", "My Room");
    gridDocuments.insertOne(testGridWithId);
    gridController = new GridController(db);
  }

  @Test
  void canGetAllGrids() throws IOException {
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    gridController.getGrids(ctx);
    verify(ctx).json(gridListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(db.getCollection("grids").countDocuments(), gridListCaptor.getValue().size());
  }

  @Test
  public void canBuildController() throws IOException {
    Javalin mockServer = Mockito.mock(Javalin.class);
    gridController.addRoutes(mockServer);
    /*
     * This tests how many 'get', 'delete' and 'post' routes there are
     * Change the get count to: verify(mockServer, Mockito.atLeast(2)).get(any(),
     * any());
     * when reinstate get words by group
     */
    verify(mockServer, Mockito.atLeastOnce()).get(any(), any());
    verify(mockServer, Mockito.atLeastOnce()).post(any(), any());
  }

  // tests for getGrid()
  @Test
  void canGetGridById() throws IOException {
    MongoCollection<Document> gridDocuments = db.getCollection("grids");
    Document testGrid = new Document()
      .append("roomID", "testRoomID")
      .append("grid", new ArrayList<>())
      .append("_id", new ObjectId());
    gridDocuments.insertOne(testGrid);
    String targetGridId = testGrid.getObjectId("_id").toHexString();

    when(ctx.pathParam("id")).thenReturn(targetGridId);

    gridController.getGrid(ctx);

    verify(ctx).json(gridCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(targetGridId, gridCaptor.getValue()._id);
  }

  @Test
  public void getGridWithBadID() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      gridController.getGrid(ctx);
    });

    assertEquals("The requested grid id wasn't a legal Mongo Object ID", exception.getMessage());
  }

  @Test
  public void getGridWithNonexistentId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("588935f5c668650dc77df581");

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      gridController.getGrid(ctx);
    });

    assertEquals("The requested grid was not found", exception.getMessage());
  }

  @Test
  public void canSaveGrid() throws IOException {
    Grid newGrid = new Grid();

    newGrid.name = "Test Name";
    newGrid.roomID = "Test Room";
    newGrid.grid = new GridCell[2][2];
    newGrid.lastSaved = new Date();

    String newGridJson = javalinJackson.toJsonString(newGrid, Grid.class);

    when(ctx.bodyValidator(Grid.class)).thenReturn(new BodyValidator<>(newGridJson, Grid.class, () -> newGrid));

    gridController.saveGrid(ctx);

    verify(ctx).status(HttpStatus.CREATED);
    verify(ctx).json(gridCaptor.capture());

    Grid capturedGrid = gridCaptor.getValue();
    assertNotNull(capturedGrid._id);

    Document addedGrid = db.getCollection("rooms")
        .find(new Document("_id", new ObjectId(capturedGrid._id))).first();
  }

  @Test
  void canGetGridsByRoom() throws IOException {
    MongoCollection<Document> gridDocuments = db.getCollection("grids");
    Document testGrid1 = new Document()
        .append("roomID", "room1")
        .append("grid", new ArrayList<>())
        .append("name", "Test Grid 1")
        .append("lastSaved", new Date());
    Document testGrid2 = new Document()
        .append("roomID", "room1")
        .append("grid", new ArrayList<>())
        .append("name", "Test Grid 2")
        .append("lastSaved", new Date());
    gridDocuments.insertOne(testGrid1);
    gridDocuments.insertOne(testGrid2);

    when(ctx.pathParam("roomID")).thenReturn("room1");

    gridController.getGridsByRoom(ctx);

    verify(ctx).json(gridListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals(2, gridListCaptor.getValue().size());
  }

  @Test
  void deleteGrid() throws IOException {
    String testID = gridId.toHexString();
    when(ctx.pathParam("id")).thenReturn(testID);

    assertEquals(1, db.getCollection("grids")
        .countDocuments(eq("_id", new ObjectId(testID))));

    gridController.deleteGrid(ctx);

    verify(ctx).status(HttpStatus.OK);

    assertEquals(0, db.getCollection("grids")
        .countDocuments(eq("_id", new ObjectId(testID))));
  }

  @Test
  void deleteGridWithBadID() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      gridController.deleteGrid(ctx);
    });

    assertEquals("The requested grid id wasn't a legal Mongo Object ID", exception.getMessage());
  }

  @Test
  public void deleteGridWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      gridController.deleteGrid(ctx);
    });

    assertEquals("The requested grid was not found", exception.getMessage());
  }

}
