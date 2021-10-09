import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class UnauthorizedDeliveries extends JFrame {

    private Thread portListenerThread;

    private final TextArea logsArea;
    //private int counter = 0;

    private String loginsPath = "";
    private String postingsPath = "";
    private String dbUrl = "jdbc:postgresql://localhost:5432/a1";
    private String dbUsername = "";
    private String dbPassword = "";

    public UnauthorizedDeliveries() {
        // поле для логов
        logsArea = new TextArea("", 0, 0, TextArea.SCROLLBARS_VERTICAL_ONLY);
        logsArea.setEditable(false);
        logsArea.setFocusable(false);
        logsArea.setBounds(10, 10, 470, 300);
        logsArea.setVisible(true);
        this.add(logsArea);

        // путь к файлу логинов
        TextField loginsFilePath = new TextField();
        loginsFilePath.setEditable(false);
        loginsFilePath.setBounds(10, 330, 300, 30);
        loginsFilePath.setVisible(true);
        this.add(loginsFilePath);

        // кнопка выбора файла логинов
        JButton chooseLoginsButton = new JButton();
        chooseLoginsButton.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Choose a file", FileDialog.LOAD);
            fd.setDirectory(System.getProperty("user.dir"));
            fd.setFile("*.csv");
            fd.setVisible(true);
            String filename = fd.getDirectory() + fd.getFile();
            if (fd.getFile() == null) {
                log("logins choice cancelled");
            } else {
                loginsPath = filename;
                loginsFilePath.setText(filename);
                log(filename + " has been selected");
                if (!postingsPath.isEmpty() && !loginsPath.isEmpty()) {
                    dbMagic();
                }
            }
        });
        chooseLoginsButton.setText("choose logins.csv");
        chooseLoginsButton.setVisible(true);
        chooseLoginsButton.setBounds(320, 330, 160, 30);
        this.add(chooseLoginsButton);

        // путь к файлу постингов
        TextField postingsFilePath = new TextField();
        postingsFilePath.setEditable(false);
        postingsFilePath.setBounds(10, 370, 300, 30);
        postingsFilePath.setVisible(true);
        this.add(postingsFilePath);

        // кнопка выбора файла постингов
        JButton choosePostingsButton = new JButton();
        choosePostingsButton.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Choose a file", FileDialog.LOAD);
            fd.setDirectory(System.getProperty("user.dir"));
            fd.setFile("*.csv");
            fd.setVisible(true);
            String filename = fd.getDirectory() + fd.getFile();
            if (fd.getFile() == null) {
                log("postings choice cancelled");
            } else {
                postingsPath = filename;
                postingsFilePath.setText(filename);
                log(filename + " has been selected");
                if (!postingsPath.isEmpty() && !loginsPath.isEmpty()) {
                    dbMagic();
                }
            }
        });
        choosePostingsButton.setText("choose postings.csv");
        choosePostingsButton.setVisible(true);
        choosePostingsButton.setBounds(320, 370, 160, 30);
        this.add(choosePostingsButton);

        // url базы данных
        Label dbLabel = new Label("database url");
        dbLabel.setBounds(10, 400, 470, 30);
        this.add(dbLabel);
        TextField databaseUrl = new TextField();
        databaseUrl.setEditable(true);
        databaseUrl.setBounds(10, 430, 470, 30);
        databaseUrl.setVisible(true);
        databaseUrl.setText(dbUrl);
        databaseUrl.addTextListener(e -> {
            dbUrl = databaseUrl.getText();
            //log("db url was changed to: " + dbUrl);
        });
        this.add(databaseUrl);

        // username для подключения к базе данных
        Label usernameLabel = new Label("database username");
        usernameLabel.setBounds(10, 460, 230, 30);
        this.add(usernameLabel);
        TextField databaseUsername = new TextField();
        databaseUsername.setEditable(true);
        databaseUsername.setBounds(10, 490, 230, 30);
        databaseUsername.setVisible(true);
        databaseUsername.setText(dbUsername);
        databaseUsername.addTextListener(e -> {
            dbUsername = databaseUsername.getText();
            //log("db username was changed to: " + dbUsername);
        });
        this.add(databaseUsername);

        // password для подключения к базе данных
        Label passwordLabel = new Label("database password");
        passwordLabel.setBounds(250, 460, 230, 30);
        this.add(passwordLabel);
        JPasswordField databasePassword = new JPasswordField();
        databasePassword.setEditable(true);
        databasePassword.setBounds(250, 490, 230, 30);
        databasePassword.setVisible(true);
        databasePassword.setText(dbPassword);
        databasePassword.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                dbPassword = String.valueOf(databasePassword.getPassword());
                // log("db password was changed to: " + dbPassword);
            }
        });
        this.add(databasePassword);

        // поток для обработки get запросов
        portListenerThread = new Thread(() -> {
            var server = new NanoHTTPD(3000) {
                @Override
                public Response serve(IHTTPSession session) {
                    if (session.getMethod() != Method.GET) {
                        log("received non GET request: " + session.getUri());
                        return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                    }
                    var uri = session.getUri();
                    if (!uri.equals("/api/document") && !uri.equals("/api/posting")) {
                        log("received non api request: " + session.getUri());
                        return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                    }
                    boolean documentDateFind = uri.equals("/api/document");

                    var params = session.getParms();
                    var keys = params.keySet();
                    boolean flag = false;
                    if (keys.contains("authorized")) {
                        String param = params.get("authorized").toLowerCase();
                        if (!param.equals("false") && !param.equals("true")) {
                            log("bad authorized parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }
                        flag = param.equals("true");
                    }

                    String querySelect = "select * from postings where";

                    boolean isYear = false;
                    int year = 0;
                    if (isYear = keys.contains("year")) {
                        String param = params.get("year");
                        if (!param.matches("\\d{4}")) {
                            log("bad year parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }

                        if (documentDateFind) {
                            querySelect += " extract(year from doc_date) =?";
                        } else {
                            querySelect += " extract(year from post_date) =?";
                        }
                    }

                    boolean isQuarter = false;
                    int startQuarter = 0, endQuarter = 0;
                    if (isQuarter = keys.contains("quarter")) {
                        String param = params.get("quarter");
                        int quarter;
                        if (param.matches("\\d")) {
                            quarter = Integer.parseInt(param);
                        } else {
                            log("bad quarter parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }
                        if (quarter <= 0 || quarter > 4) {
                            log("bad quarter parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }
                        startQuarter = quarter * 3 - 2;
                        endQuarter = quarter * 3;

                        if (documentDateFind) {
                            querySelect += " extract(month from doc_date) between ? and ?";
                        } else {
                            querySelect += " extract(month from post_date) between ? and ?";
                        }
                    }

                    boolean isMonth = false;
                    int month = 0;
                    if (isMonth = keys.contains("month")) {
                        String param = params.get("month");
                        if (param.matches("\\d{1,2}")) {
                            month = Integer.parseInt(param);
                        } else {
                            log("bad month parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }
                        if (month <= 0 || month > 12) {
                            log("bad month parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }

                        if (documentDateFind) {
                            querySelect += " extract(month from doc_date) =?";
                        } else {
                            querySelect += " extract(month from post_date) =?";
                        }
                    }

                    boolean isDay = false;
                    int day = 0;
                    if (isDay = keys.contains("day")) {
                        String param = params.get("day");
                        if (param.matches("\\d{1,2}")) {
                            day = Integer.parseInt(param);
                        } else {
                            log("bad day parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }
                        if (day <= 0 || day > 32) {
                            log("bad day parameter");
                            return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                        }

                        if (documentDateFind) {
                            querySelect += " extract (day from doc_date)=?";
                        } else {
                            querySelect += " extract (day from post_date)=?";
                        }
                    }

                    if (flag) {
                        querySelect += " and is_active=true";
                    }

                    try (Connection con = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                         PreparedStatement statement = con.prepareStatement(querySelect)) {
                        int counter = 1;
                        if (isYear) {
                            statement.setInt(counter++, year);
                        }
                        if (isQuarter) {
                            statement.setInt(counter++, startQuarter);
                            statement.setInt(counter++, endQuarter);
                        }
                        if (isMonth) {
                            statement.setInt(counter++, month);
                        }
                        if (isDay) {
                            statement.setInt(counter, day);
                        }

                        var rs = statement.executeQuery();

                        JSONArray json = new JSONArray();
                        ResultSetMetaData rsmd = rs.getMetaData();
                        while (rs.next()) {
                            int numColumns = rsmd.getColumnCount();
                            JSONObject obj = new JSONObject();
                            for (int i = 1; i <= numColumns; i++) {
                                String column_name = rsmd.getColumnName(i);
                                obj.put(column_name, rs.getObject(column_name));
                            }
                            json.put(obj);
                        }
                        log("json returned");
                        return new Response(Response.Status.OK, "application/json", json.toString());
                    } catch (Exception e) {
                        log(e.getMessage());
                        return new Response(Response.Status.FORBIDDEN, "application/json", "{}");
                    }
                }
            };
            try {
                server.start();
            } catch (IOException e) {
                log(e.getMessage());
            }

        });
        portListenerThread.start();

        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        this.setTitle("Unauthorized deliveries");
        this.setSize(510, 570);
        this.setLayout(null);
        this.setResizable(false);
        this.setVisible(true);
    }

    private synchronized void log(String message) {
        logsArea.append(message + '\n');
        System.out.println(message);
    }

    private void dbMagic() {
        Scanner loginsScanner;
        Scanner postingsScanner;
        try {
            loginsScanner = new Scanner(new File(loginsPath));
            postingsScanner = new Scanner(new File(postingsPath));
        } catch (FileNotFoundException e) {
            log(e.getMessage());
            return;
        }

        loginsScanner.nextLine();
        postingsScanner.nextLine();

        var logins = new ArrayList<Login>();
        while (loginsScanner.hasNextLine()) {
            String line = loginsScanner.nextLine();
            if (line.isBlank())
                continue;
            var temp = line.split(",");
            String application = temp[0].trim();
            String appAccountName = temp[1].trim();
            boolean isActive = temp[2].trim().equals("True");
            String jobTitle = temp[3].trim();
            String department = temp[4].trim();
            Login login = new Login(application, appAccountName, isActive, jobTitle, department);
            logins.add(login);
            // log("read from logins" + login.toString());
        }

        var postings = new ArrayList<Posting>();
        while (postingsScanner.hasNextLine()) {
            String line = postingsScanner.nextLine();
            if (line.isBlank())
                continue;
            var temp = line.split(";");
            String id = temp[0].trim();
            int num = Integer.parseInt(temp[1].trim());
            String docDate = temp[2].trim();
            String postDate = temp[3].trim();
            String material = temp[4].trim();
            int quantity = Integer.parseInt(temp[5].trim());
            String bun = temp[6].trim();
            String price = temp[7].trim();
            String currency = temp[8].trim();
            String username = temp[9].trim();
            Login toFound = new Login(null, username, true, null, null);
            boolean isAuthorized = logins.contains(toFound);
            Posting posting = new Posting(id, num, docDate, postDate, material, quantity, bun, price, currency, username, isAuthorized);
            postings.add(posting);
            //log("read from logins" + posting.toString());
        }

        String loginsTableCreateQuery = "create table if not exists logins\n" +
                "(\n" +
                "    application text,\n" +
                "    username    text,\n" +
                "    is_active   boolean,\n" +
                "    job_title   text,\n" +
                "    department  text\n" +
                ");";

        String postingsTableCreateQuery = "create table if not exists postings1\n" +
                "(\n" +
                "    id        text,\n" +
                "    num       integer,\n" +
                "    doc_date  date,\n" +
                "    post_date date,\n" +
                "    material  text,\n" +
                "    \"BUn\"     varchar(10),\n" +
                "    price     text,\n" +
                "    currency  varchar(10),\n" +
                "    username  text,\n" +
                "    quantity  integer\n" +
                ");";

        //create table for logins if needed
        try (Connection con = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             Statement statement = con.createStatement()) {
            statement.executeQuery(loginsTableCreateQuery);
            log("logins table was created");
        } catch (SQLException e) {
            log("logins table exists");
        }

        //create table for postings if needed
        try (Connection con = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             Statement statement = con.createStatement()) {
            statement.executeQuery(postingsTableCreateQuery);
            log("logins table was created");
        } catch (SQLException e) {
            log("logins table exists");
        }

        //import data
        String loginImport = "insert into logins(application, username, is_active, job_title, department) values (?, ?, ?, ?, ?)";
        for (var login : logins) {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                 PreparedStatement statement = con.prepareStatement(loginImport)) {
                statement.setString(1, login.Application);
                statement.setString(2, login.AppAccountName);
                statement.setBoolean(3, login.IsActive);
                statement.setString(4, login.JobTitle);
                statement.setString(5, login.Department);
                statement.executeUpdate();
            } catch (SQLException e) {
                log(e.getMessage());
                return;
            }
        }
        //log("logins data was imported");

        String postingImport = "insert into postings(id, num, doc_date, post_date, material, bun, price, currency, username, quantity, is_active) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
        for (var posting : postings) {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                 PreparedStatement statement = con.prepareStatement(postingImport)) {
                statement.setString(1, posting.Id);
                statement.setInt(2, posting.Num);
                statement.setDate(3, new Date(df.parse(posting.DocDate).getTime()));
                statement.setDate(4, new Date(df.parse(posting.PostDate).getTime()));
                statement.setString(5, posting.Material);
                statement.setString(6, posting.BUn);
                statement.setString(7, posting.Price);
                statement.setString(8, posting.Currency);
                statement.setString(9, posting.Username);
                statement.setInt(10, posting.Quantity);
                statement.setBoolean(11, posting.IsAuthorized);
                statement.executeUpdate();
            } catch (SQLException | ParseException e) {
                log(e.getMessage());
                return;
            }
        }
        //log("postings data was imported");
        log("data has been imported");
    }

    public static void main(String[] args) throws ParseException {
        new UnauthorizedDeliveries();
        /*
        var file = new File("F:\\Projects\\Java\\A1\\resources\\postings.csv");
        Scanner scanner = new Scanner(file);
        var list = new ArrayList<String>();
        while (scanner.hasNextLine()) {
            String temp = scanner.nextLine();
            list.add(temp.replaceAll(";", ","));
        }
        var writer = new FileWriter(file);
        for (var sentence : list) {
            writer.write(sentence + "\n");
        }
        writer.close();

        */
    }

    private static class Login {
        String Application;
        String AppAccountName;
        boolean IsActive;
        String JobTitle;
        String Department;

        public Login(String application, String appAccountName, boolean isActive, String jobTitle, String department) {
            Application = application;
            AppAccountName = appAccountName;
            IsActive = isActive;
            JobTitle = jobTitle;
            Department = department;
        }

        @Override
        public String toString() {
            return "Login{" +
                    "Application='" + Application + '\'' +
                    ", AppAccountName='" + AppAccountName + '\'' +
                    ", IsActive='" + IsActive + '\'' +
                    ", JobTitle='" + JobTitle + '\'' +
                    ", Department='" + Department + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Login)) return false;
            Login login = (Login) o;
            return AppAccountName.equals(login.AppAccountName) && IsActive == ((Login) o).IsActive && IsActive;
        }

        @Override
        public int hashCode() {
            return Objects.hash(AppAccountName);
        }
    }

    private static class Posting {
        String Id;
        int Num;
        String DocDate;
        String PostDate;
        String Material;
        int Quantity;
        String BUn;
        String Price;
        String Currency;
        String Username;
        boolean IsAuthorized;

        public Posting(String id, int num, String docDate, String postDate, String material, int quantity, String BUn, String price, String currency, String username, boolean isAuthorized) {
            Id = id;
            Num = num;
            DocDate = docDate;
            PostDate = postDate;
            Material = material;
            Quantity = quantity;
            this.BUn = BUn;
            Price = price;
            Currency = currency;
            Username = username;
            IsAuthorized = isAuthorized;
        }

        @Override
        public String toString() {
            return "Posting{" +
                    "Id='" + Id + '\'' +
                    ", Num='" + Num + '\'' +
                    ", DocDate='" + DocDate + '\'' +
                    ", PostDate='" + PostDate + '\'' +
                    ", Material='" + Material + '\'' +
                    ", Quantity=" + Quantity +
                    ", BUn='" + BUn + '\'' +
                    ", Price='" + Price + '\'' +
                    ", Currency='" + Currency + '\'' +
                    ", Username='" + Username + '\'' +
                    ", IsAuthorized='" + IsAuthorized + '\'' +
                    '}';
        }
    }
}
