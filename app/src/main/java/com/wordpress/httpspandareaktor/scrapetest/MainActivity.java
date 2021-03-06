package com.wordpress.httpspandareaktor.scrapetest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements HunterSeeker{

    //this is the webview browser that will load pages and later extract the HTML with JavaScript
    private WebView browser;

    //arraylists to store visited and unvisited urls
    private HashSet<String> visitedLinks = new HashSet<>();
    private LinkedList<String> collectedLinks = new LinkedList<>();
    private HashSet<String> masterEmailSet = new HashSet<>();


    //first link visited, and the last HTML result received by WebView
    private String firstLinkAsString = "";


    //textviews to show emails found and html source
    TextView emailDisplay;
    byte emailsFound = 0;
    String lastHtmlResult;

    //this EditText is where the user's URL input goes, queried URL is another store (created URL) of the input
    private EditText urlField;

    //the progress bar containing the animation + update text
    LinearLayout progressBar;

    //field containing the search term (if any) which is stored as a string
    EditText searchTermField;
    String searchTerm;

    //the top section contains the search term and URL edit text boxes
    LinearLayout topSection;

    //text below progress bar
    TextView progressText;

    //is the WebView already crawling?
    boolean crawlComplete;

    //LinearLayout containing the reset button
    LinearLayout resetSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchTermField = (EditText) findViewById(R.id.searchTermField);
        urlField = (EditText) findViewById(R.id.inputURL);
        progressBar = (LinearLayout) findViewById(R.id.progressBar);
        topSection = (LinearLayout) findViewById(R.id.topSection);
        progressText = (TextView) findViewById(R.id.progressText);
        emailDisplay = (TextView) findViewById(R.id.emailDisplay);
        resetSection = (LinearLayout) findViewById(R.id.resetSection);

        //done finding the views, now make browser by finding view and using helper method

        browser = (WebView) findViewById(R.id.browser);
        setupBrowser();

    }

    public void clearFields(View view){
        //clear the two edit text input fields
        searchTermField.setText("");
        urlField.setText("");
    }

    public void extractButton(View view) {
        //first hide the soft input as it is not needed
        InputMethodManager inputManager = (InputMethodManager)getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);

        Log.v("extractButton", " initialized with URL field as:" + urlField.getText().toString());

        //doesn't seem to be necessary
//        if (!networkAvailable()) {
//            //Error message if the network is unavailable
//            Toast.makeText(this, "Network unavailable!", Toast.LENGTH_SHORT).show();
//            return;
//        }

        //User just typed in a URL and requested fetch
        if (!urlField.getText().toString().equals("")) {
            //if not empty, try to build URL, makeURL shoudld catch MalformedURLException
            URL currentURL = NetworkUtils.makeURL(NetworkUtils.insertWebSubdomian(urlField.getText().toString()));

            if (currentURL != null) {
                Log.v("extractButton", " says URL field is acceptable");
                firstLinkAsString = currentURL.toString();
                searchTerm = searchTermField.getText().toString();
                //if the currentlyRunning boolean says there are no current tasks going, make a new one and reference it

                //set up the UI while the user waits, show the WebView as well
                browser.setVisibility(View.VISIBLE);
                topSection.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
//                emailDisplay.setVisibility(View.VISIBLE);

                //hit URL for an initial pull
                hitURL(currentURL.toString());

            } else {
                Toast.makeText(this, "Bad URL! Try again", Toast.LENGTH_SHORT).show();
            }

        } else {
            Toast.makeText(this, "Cannot extract from an empty URL!", Toast.LENGTH_SHORT).show();
        }
    }

    private void hitURL(String URL){
        // Simplest usage: note that an exception will NOT be thrown
        // if there is an error loading this page (see below).

        //add this URL to visitedLinks and visit it
        Log.v(".hitURL", " now crawling at: " + URL);
        String refinedURL = NetworkUtils.makeURL(URL).toString();
        visitedLinks.add(refinedURL);
        browser.loadUrl(refinedURL);


        // OR, you can also load from an HTML string:
//        String summary = "<html><body>You scored <b>192</b> points.</body></html>";
//        webview.loadData(summary, "text/html", null);
        // ... although note that there are restrictions on what this HTML can do.
        // See the JavaDocs for loadData() and loadDataWithBaseURL() for more info.
    }

    private void pullLinks(String htmlPage) {
        //this method pulls links from a page, if they haven't been visited, add into unvisited ArrayList<URL>

        Document doc = Jsoup.parse(htmlPage);
        Elements links = doc.select("a[href]");

        for (Element link : links) {

            String possibleUrl = link.attr("abs:href");

            if (!possibleUrl.equals("")) {
//                Log.v("pullLinks", " will try to make URL from" + possibleUrl);
                //if the link attr isn't empty, make a URL
                URL theUrl = NetworkUtils.makeURL(possibleUrl);

                if (RegexUtils.urlDomainNameMatch(firstLinkAsString, theUrl.toString())) {
                    //if the string version of url is within the same domain as original query
                    if (!visitedLinks.contains(theUrl.toString())) {
//                        Log.v("DLAsyncTask.pullLinks", " thinks that " + theUrl.toString() + " wasn't visited, add into collected...");
                        collectedLinks.add(theUrl.toString());
                    }
                }
            }

        }
    }

    private void cleanCollectedLinks() {
        //iterator to go over and clean out collectedLinks HashSet
        for (Iterator itr = visitedLinks.iterator(); itr.hasNext(); ) {
            String thisURL = (String) itr.next();
            if (urlInLinkedList(NetworkUtils.makeURL(thisURL), collectedLinks)) {
                collectedLinks.remove(thisURL.toString());
//                Log.v("DLasync.cleanCollected", " from CollectedLinks, just cleaned: " + thisURL);
//                Log.v(".cleanCollected", " collected set is now:" + collectedLinks.toString());
            }
        }

    }

    private void sleepMilliseconds(int time) {
        //try sleeping randomly up to time milliseconds
        //prevent repeated suspicious activity from server
        int multipliedParam = (int) (Math.random() * time + 1);

        try {
            TimeUnit.MILLISECONDS.sleep(multipliedParam);
            Log.v("DLASync.sleep", " sleep " + multipliedParam + " milliseconds...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean urlInLinkedList(URL url, LinkedList<String> set){
        //checks if the URL is in a provided HashSet with an improved for loop
        boolean returnBoolean = false;

        for (String setItem : set){
            if (NetworkUtils.urlHostPathMatch(NetworkUtils.makeURL(setItem), url)) {
//                Log.v("DLAsync.urlInHashSet", " just found " + url.toString() + " in " + set.toString());
                returnBoolean = true;
            }
        }
        return returnBoolean;
    }

    @Override
    public void onFinishPage(HashSet<String> set, String html) {
        if (set.size() != 0) {
            for (String string : set) {
                emailsFound++;
                masterEmailSet.add(string);
                Log.v("onFinishPage", "masterEmailSet length " + " +1 , total length: " + masterEmailSet.size());
            }
        }

        displayMasterEmails();
    }

    @Override
    public void onFinishPull(boolean finished) {
        if (finished) {setPostCrawlUI(); }
    }

    @Override
    public void onSendUpdate(String updateItem) {
        progressText.setText(updateItem);
    }

    private void displayMasterEmails(){
        StringBuilder masterEmails = new StringBuilder();
        for (String email : masterEmailSet){
            Log.v("Stringing over", "" + email);
            masterEmails.append(email);
            masterEmails.append("\n");
        }
        emailDisplay.setText(masterEmails);
    }

    public void killTask(View view) {
        //user wants to kill the AsyncTask
        Log.v("MainActivity.killTask", " triggered");
        if (!crawlComplete) {
            crawlComplete = true;
            browser.stopLoading();
            browser.clearHistory();
            browser.clearCache(true);
            browser.loadUrl("about:blank");
            browser.getSettings().setJavaScriptEnabled(true);
            setPostCrawlUI();
        }
    }

    public void setPostCrawlUI(){
        resetSection.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);
        //TODO: switch next line to visible once testing is complete
        topSection.setVisibility(View.GONE);
        displayMasterEmails();
    }

    public void revertApp(View view){
        Log.v("revertApp", " has been called, purging all HashSets and resetting UI");
        //revert app to its original state, first by reverting UI elements then resetting data
        resetSection.setVisibility(View.GONE);
        topSection.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        browser.setVisibility(View.GONE);

        emailDisplay.setText("");
        masterEmailSet.clear();
        visitedLinks.clear();
        collectedLinks.clear();
        Log.v("visit & collect sizes:", " " + visitedLinks.size() + " " + collectedLinks.size());
        emailsFound = 0;
        firstLinkAsString = "";
        lastHtmlResult = "";

    }

    public boolean networkAvailable() {
        //returns boolean to determine whether network is available, requires ACCESS_NETWORK_STATE permission
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        // if network is off, networkInfo will be null
        //otherwise check if we are connected
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public void setupBrowser() {
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setUserAgentString("Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.4) Gecko/20100101 Firefox/4.0");
        browser.getSettings().setDomStorageEnabled(true);
        //try blocking the loading of images
        browser.getSettings().setBlockNetworkImage(true);
        browser.addJavascriptInterface(new MyJavaScriptInterface(this, this), "HtmlOut");

        browser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                //make strings for the update messages
                final String onPageStartedUrl = url;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onSendUpdate("Initiating page: " + onPageStartedUrl);
                    }
                });
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                //TODO: learn to achieve same effect with new version?
                if (url.contains("dc.services.visualstudio.com/") || url.endsWith(".png") ||
                        url.endsWith(".ico") || url.endsWith(".css")){
                    Log.v("shouldInterceptRequest", " intercepted a request to: " + url);
                    return null;
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                final String onLoadResourceUrl = url;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onSendUpdate("Loading resource: " + onLoadResourceUrl);
                    }
                });
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                //call processHTML from custom HtmlOut JS interface onPageFinished
                browser.loadUrl("javascript:HtmlOut.processHTML" +
                        "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                //TODO: test if this overridden method works given a failed page
                Log.v("WVClient.onReceiveError", " receieved an error" + error);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onSendUpdate("WVClient has received an error!");
                    }
                });

                //do what would be done in processHTML but avoid anything to do with scraping, move onto next page
                crawlComplete = false;

                if (masterEmailSet.size() > 20){
                    //if more than twenty emails have been discovered, the crawl is done
                    crawlComplete = true;
                }

                if (masterEmailSet.size() > 0 && !searchTerm.equals("")){
                    //if at least one email with the search term has been found, crawl is done
                    crawlComplete = true;
                }


                if (collectedLinks.iterator().hasNext() && !crawlComplete){
                    //if there's another link and crawl isn't deemed complete, hit next URL
                    browser.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.v("processHTML", " loading page on browser:" + collectedLinks.iterator().next());
                            visitedLinks.add(collectedLinks.iterator().next());
                            browser.loadUrl(collectedLinks.iterator().next());
                        }
                    });
                }
            }
        });
    }

    private class MyJavaScriptInterface {
        //This JS interface is called from WebView after onFinish

        private HunterSeeker mHunterSeeker;
        private Context ctx;

        MyJavaScriptInterface(Context ctx, HunterSeeker hunterSeeker) {
            //this interface will allow javascript to communicate with Android
            this.ctx = ctx;
            this.mHunterSeeker = hunterSeeker;
        }

        @JavascriptInterface
        public void processHTML(String html) {
            lastHtmlResult = html;
            //called when browser finished loading a page

            //below is an alert dialog to show the html directly
//            new AlertDialog.Builder(ctx).setTitle("HTML").setMessage(html)
//                    .setPositiveButton(android.R.string.ok, null).setCancelable(false).create().show();

            //every time we call purify, we get a hashset, we want to copy that set into master
            Log.v("processHTML", " just received html: " + html);
            final HashSet<String> emailsOnPage = RegexUtils.purify(lastHtmlResult, searchTerm);
            if ((!html.equals(""))  && emailsOnPage.size() > 0) {
                //if there was html with emails on the page
                try {
                    runOnUiThread(new Runnable() {
                        //create a new thread to call onFinishPage which can alter the UI
                        //remember that Activity.runOnUiThread is specially made for this!
                        @Override
                        public void run() {
                            mHunterSeeker.onFinishPage(emailsOnPage, lastHtmlResult);
                            onSendUpdate("RegexUtils.purify returned " + emailsOnPage.size() + " items");
                        }
                    });
                } catch (NullPointerException e ) {
                    e.printStackTrace();
                }
            }

            //from the extracted html, pull links for the crawler and run clean to be certain they haven't been visited
            //TODO: check if the cleanCollectedLinks is necessary, perhaps implement the same functionality into pullLinks
            pullLinks(html);
            cleanCollectedLinks();

            //boolean flag to see if the crawler is done
            crawlComplete = false;

            if (masterEmailSet.size() > 20){
                //if more than twenty emails have been discovered, the crawl is done
                crawlComplete = true;
            }

            if (masterEmailSet.size() > 0 && !searchTerm.equals("")){
                //if at least one email with the search term has been found, crawl is done
                crawlComplete = true;
            }


            if (collectedLinks.iterator().hasNext() && !crawlComplete){
                //if there's another link and crawl isn't deemed complete, hit next URL
                browser.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.v("processHTML", " loading page on browser:" + collectedLinks.iterator().next());
                        onSendUpdate("Next hyperlink: " + collectedLinks.iterator().next());
                        visitedLinks.add(collectedLinks.iterator().next());
                        long timer1 = System.nanoTime();
                        browser.loadUrl(collectedLinks.iterator().next());
                        long timer1complete = System.nanoTime() - timer1;
                        Log.v("browser.loadUrl", " took" + timer1complete + " ns");
                    }
                });
            }

            Log.v("processHtml", " will call onFinishPull on HunterSeeker instance " + mHunterSeeker.toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onSendUpdate("Finishing the call to processHTML method...");
                    mHunterSeeker.onFinishPull(crawlComplete);
                }
            });

        }
    }

}
