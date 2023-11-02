package cs1302.gallery;

import java.net.http.HttpClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.geometry.Orientation;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.util.ArrayList;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import javafx.scene.text.Font;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    private ItunesResponse parsedResponse;
    private HttpResponse<String> response;
    private String url;
    private int successfulSearches;
    private ArrayList<String> imageUrl;
    private String[] usedImageUrl;
    private Image[] images;

    public static final String DEFAULT_IMG = "file:resources/default.png";
    public final Insets padding = new Insets(5, 5, 5, 5);
    public final String directions = "Type in a term, select a media type, then click the button.";
    private Stage stage;
    private Scene scene;
    private VBox root;

    private HBox searchLayer;
    private Button playPauseButton;
    private boolean randomReplacementOn;

    private Label search;
    private TextField textField;
    private ComboBox<String> dropDownMenu;
    private Button getImagesButton;

    private HBox textLayer;
    private Text directionsText;

    private HBox imageLayer;
    private TilePane imageContent;
    private ImageView[] imageViewArray;
    private Image image;

    private HBox progressLayer;
    private ProgressBar progressBar;
    private Text creditItunesText;

    private Timer timer;
    private TimerTask replacementTask;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox();
        parsedResponse = null;
        successfulSearches = 0;
        imageUrl = new ArrayList<String>();
        usedImageUrl = new String[20];
        searchLayer = new HBox();
        searchLayer.setPadding(padding);
        searchLayer.setSpacing(5);
        playPauseButton = new Button("Play");
        HBox.setHgrow(playPauseButton, Priority.NEVER);
        randomReplacementOn = false;
        playPauseButton.setDisable(true);
        playPauseButton.setOnAction(e -> {
            if (randomReplacementOn == false) {
                runNow(() -> randomReplacement(e));
            } else {
                runNow(() -> stopRandomReplacement(e));
            } // if
        });
        search = new Label("Search:");
        textField = new TextField("Enter something here");
        dropDownMenu = new ComboBox<String>();
        dropDownMenu.getItems().addAll(
            "movie",
            "podcast",
            "music",
            "musicVideo",
            "audiobook",
            "shortFilm",
            "tvShow",
            "software",
            "eBook",
            "all"
        );
        dropDownMenu.setValue("music");
        HBox.setHgrow(dropDownMenu, Priority.NEVER);
        getImagesButton = new Button("Get Images");
        getImagesButton.setOnAction(e -> runNow(() -> getImages(e)));
        textLayer = new HBox();
        textLayer.setPadding(padding);
        directionsText = new Text(directions);
        imageLayer = new HBox();
        imageContent = new TilePane();
        imageViewArray = new ImageView[20];
        for (int i = 0; i < 20; i++) {
            image = new Image(DEFAULT_IMG);
            imageViewArray[i] = new ImageView(image);
            imageContent.getChildren().add(imageViewArray[i]);
        } // for
        progressLayer = new HBox();
        progressLayer.setPadding(padding);
        progressLayer.setSpacing(5);
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(400);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        creditItunesText = new Text("Images Provided by iTunes Search API.");
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        searchLayer.getChildren().addAll(playPauseButton,
            search, textField, dropDownMenu,
            getImagesButton);
        textLayer.getChildren().add(directionsText);
        imageLayer.getChildren().add(imageContent);
        progressLayer.getChildren().addAll(progressBar, creditItunesText);
        searchLayer.setAlignment(Pos.CENTER);
        imageLayer.setAlignment(Pos.CENTER);
        progressLayer.setAlignment(Pos.CENTER);
        root.getChildren().addAll(searchLayer, textLayer, imageLayer, progressLayer);
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.root);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /** {@inheritDoc} */
    @Override
    public void stop() {
        System.out.println("stop() called");
    } // stop

    /**
     * This happens when the "Get Images" button is clicked.
     * Pictures are downloaded and the first 20 images are displayed.
     *
     * @param e ActionEvent that occurs when "Get Images" button is clicked.
     */
    public void getImages(ActionEvent e) {
        try {
            Platform.runLater(() -> {
                progressBar.setProgress(0);
                playPauseButton.setDisable(true);

                getImagesButton.setDisable(true);
                directionsText.setText("Getting images...");
                playPauseButton.setText("Play");
            });
            if (randomReplacementOn) {
                Platform.runLater(() -> stopRandomReplacement(e));
            } // if
            sendRequest();
            downloadDistinctImages();

            for (int i = 0; i < 20; i++) {
                imageViewArray[i].setImage(images[i]);
                usedImageUrl[i] = imageUrl.get(i);
                imageViewArray[i].setFitWidth(100);
                imageViewArray[i].setFitHeight(100);
            } // for

            Platform.runLater(() -> {
                playPauseButton.setDisable(false);
                getImagesButton.setDisable(false);
                directionsText.setText(url);
            });
            successfulSearches++;
        } catch (IOException | InterruptedException | NullPointerException |
            IllegalArgumentException exception) {
            error(exception);
        } // try
    } // loadImages

    /**
     * This happens when the "Play" button is clicked. Every 2 seconds, a new distinct image
     * will replace one of the images that are currently on the screen. This will end when user
     * clicks the "Pause" button.
     * @param e ActionEvent that occurs when user clicks the "Play" button.
     */
    public void randomReplacement(ActionEvent e) {
        timer = new Timer();
        randomReplacementOn = true;
        Platform.runLater(() -> playPauseButton.setText("Pause"));

        replacementTask = new TimerTask() {
                public void run() {
                    boolean checking = true;
                    final int viewerNum = (int)(Math.random() * 20);
                    int imageNum = 0;
                    String newUrl = "";

                    while (checking) {
                        checking = false;
                        imageNum = (int)(Math.random() * imageUrl.size());
                        for (int i = 0; i < 20; i++) {
                            if (usedImageUrl[i].equals(imageUrl.get(imageNum))) {
                                checking = true;
                            } // if
                        } // for
                    } // while

                    final int number = imageNum;

                    Platform.runLater(() -> imageViewArray[viewerNum].setImage(images[number]));
                    usedImageUrl[viewerNum] = imageUrl.get(number);
                } // run
            };
        Platform.runLater(() -> timer.schedule(replacementTask, 0, 2000));
    } // randomReplacement

    /**
     * Stops the random replacement of images.
     * @param e ActionEvent that occurs when user clicks the "Pause" button.
     */
    public void stopRandomReplacement(ActionEvent e) {
        Platform.runLater(() -> {
            timer.cancel();
            playPauseButton.setText("Play");
        });
        randomReplacementOn = false;
    } // stopRandomReplacement

    /**
     * Creates and immediately starts a new daemon thread that executes
     * {@code target.run()}. This method, which may be called from any thread,
     * will return immediately its the caller.
     * @param target the object whose {@code run} method is invoked when this
     *        thread is started
     */
    public static void runNow(Runnable target) {
        Thread t = new Thread(target);
        t.setDaemon(true);
        t.start();
    } // runNow

    /**
     * Sends a query to the Itunes API, gets back the request and parses it.
     * @throws IOException When there is an error in the input given.
     * @throws InterruptedException When there is an interruption in the process of sending and
     *         receiving query and information.
     */
    public void sendRequest() throws IOException, InterruptedException {

        final String ITUNES_API = "https://itunes.apple.com/search";
        String term = URLEncoder.encode(textField.getText(), StandardCharsets.UTF_8);
        String media = URLEncoder.
            encode(dropDownMenu.getValue().toString(), StandardCharsets.UTF_8);
        String limit = "200";
        String query = String.format("?term=%s&limit=%s&media=%s", term, limit, media);
        url = ITUNES_API + query;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();

        response = HTTP_CLIENT.send(request, BodyHandlers.ofString());

        parsedResponse =
            GSON.fromJson(response.body(), ItunesResponse.class);

        if (parsedResponse == null) {
            throw new NullPointerException();
        } // if

    } // sendRequest

    /**
     * Creates an alert and displays it using the exception given.
     * @param ex exception given
     */
    private void error(Exception ex) {
        if (parsedResponse == null) {
            throw new NullPointerException();
        }
        String content = String.format("URI: %s\n\n" +
            "Exception: %s %s distinct results found, but 21 or" +
            " more are needed.", url, ex.toString(), imageUrl.size());
        Platform.runLater(() -> {
            directionsText.setText("Last attempt to get images failed...");

            if (successfulSearches != 0) {
                playPauseButton.setDisable(false);
            } else {
                playPauseButton.setDisable(true);
            } // if
            getImagesButton.setDisable(false);
            progressBar.setProgress(1.0);

            Alert error = new Alert(AlertType.ERROR, content);
            error.getDialogPane().setPrefSize(512, 323);
            error.showAndWait();
        });
    } // error

    /**
     * Used to display the progress of downloading images.
     * @param i represents the for loop index that will be converted to progress
     * @return Runnable object that will be returned to be used in the
     *         downloadDistinctImages() method.
     */
    private Runnable displayProgress(double i) {

        double progress = (i + 1.0) / images.length;

        Runnable task = () -> {
            progressBar.setProgress(progress);
        };
        return task;
    } // displayProgress

    /**
     * Downloads the distinct images that are received in the parsed response. Also displays the
     * progress in the progress bar as images are being downloaded.
     * @throws IllegalArgumentException if there are less than 21 results
     */
    private void downloadDistinctImages() {

        imageUrl.clear();

        for (int i = 0; i < parsedResponse.results.length; i++) {
            if (!imageUrl.contains(parsedResponse.results[i].artworkUrl100)) {
                imageUrl.add(parsedResponse.results[i].artworkUrl100);
            } // if
        } // for
        images = new Image[imageUrl.size()];

        if (imageUrl.size() < 21) {
            throw new IllegalArgumentException();
        } else {
            for (int i = 0; i < images.length; i++) {
                images[i] = new Image(imageUrl.get(i));
                Platform.runLater(displayProgress(i));
            } // for
        } // if
    } // downloadDistinctImages

} // GalleryApp
