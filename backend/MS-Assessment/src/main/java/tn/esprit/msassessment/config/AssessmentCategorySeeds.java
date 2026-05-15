package tn.esprit.msassessment.config;

import tn.esprit.msassessment.entity.AnswerChoice;
import tn.esprit.msassessment.entity.Question;
import tn.esprit.msassessment.entity.QuestionCategory;
import tn.esprit.msassessment.entity.enums.DifficultyLevel;

import java.util.List;
import java.util.function.Supplier;

/**
 * Rich question bank: many categories × MCQs. Used by {@link AssessmentDataLoader} (insert if code missing).
 */
public final class AssessmentCategorySeeds {

    private AssessmentCategorySeeds() {}

    public static List<Supplier<QuestionCategory>> allSeedFactories() {
        return List.of(
                AssessmentCategorySeeds::javaOop,
                AssessmentCategorySeeds::pythonCore,
                AssessmentCategorySeeds::sqlBasics,
                AssessmentCategorySeeds::javascriptTypeScript,
                AssessmentCategorySeeds::reactWeb,
                AssessmentCategorySeeds::springBoot,
                AssessmentCategorySeeds::dockerKubernetes,
                AssessmentCategorySeeds::awsCloud,
                AssessmentCategorySeeds::gitWorkflow,
                AssessmentCategorySeeds::restApiDesign,
                AssessmentCategorySeeds::webSecurity,
                AssessmentCategorySeeds::dsaBasics,
                AssessmentCategorySeeds::genAiLlm,
                AssessmentCategorySeeds::nodeBackend,
                AssessmentCategorySeeds::mongodbNoSql,
                AssessmentCategorySeeds::angularFront,
                AssessmentCategorySeeds::linuxShell,
                AssessmentCategorySeeds::microservicesPatterns,
                // New categories for better suggestions
                AssessmentCategorySeeds::cSharpDotNet,
                AssessmentCategorySeeds::phpWeb,
                AssessmentCategorySeeds::rubyRails,
                AssessmentCategorySeeds::goLang,
                AssessmentCategorySeeds::rustSystems,
                AssessmentCategorySeeds::kotlinAndroid,
                AssessmentCategorySeeds::swiftIos,
                AssessmentCategorySeeds::flutterDart,
                AssessmentCategorySeeds::vueJs,
                AssessmentCategorySeeds::machineLearning,
                AssessmentCategorySeeds::dataScience,
                AssessmentCategorySeeds::cybersecurity,
                AssessmentCategorySeeds::devopsAdvanced,
                AssessmentCategorySeeds::blockchainWeb3,
                AssessmentCategorySeeds::gameDevUnity,
                AssessmentCategorySeeds::uxUiDesign);
    }

    public static QuestionCategory javaOop() {
        QuestionCategory cat = cat(
                "JAVA_OOP",
                "Java OOP fundamentals",
                "Classes, inheritance, polymorphism, collections — core Java for backends.");
        add(cat, "Which keyword prevents a class from being subclassed?", 2, DifficultyLevel.EASY, "java", "final", "static", "abstract", "volatile");
        add(cat, "What is the parent of all Java classes (except primitives)?", 2, DifficultyLevel.EASY, "java", "Object", "Serializable", "Cloneable", "Comparable");
        addMcq(cat, "What is the size in bits of a Java int?", 1, DifficultyLevel.EASY, "java", "32", "16", "64", "8");
        addMcq(cat, "Which collection does not allow duplicate elements?", 1, DifficultyLevel.EASY, "java", "Set", "List", "Queue", "Deque");
        addMcq(cat, "Which access modifier is the most restrictive?", 1, DifficultyLevel.EASY, "java", "private", "protected", "package-private", "public");
        addMcq(cat, "What does JVM stand for?", 1, DifficultyLevel.EASY, "java", "Java Virtual Machine", "Java Variable Model", "Joint Version Manager", "Just Virtual Memory");
        addMcq(cat, "Which exception is typically checked in Java I/O APIs?", 1, DifficultyLevel.MEDIUM, "java", "IOException", "NullPointerException", "IllegalArgumentException", "ArithmeticException");
        addMcq(cat, "Which interface marks a class for try-with-resources?", 1, DifficultyLevel.MEDIUM, "java", "AutoCloseable", "Runnable", "Comparable", "Iterable");
        addMcq(cat, "What is the default capacity growth strategy for ArrayList?", 1, DifficultyLevel.MEDIUM, "java", "Grow by a factor (~1.5x)", "Fixed forever", "Double each time", "Never grows");
        return cat;
    }

    public static QuestionCategory pythonCore() {
        QuestionCategory cat = cat("PYTHON_CORE", "Python fundamentals", "Syntax, data structures, modules — scripting to production services.");
        addMcq(cat, "What is used to create a virtual environment (common tool)?", 1, DifficultyLevel.EASY, "python", "venv / virtualenv", "npm", "gradle", "cargo");
        addMcq(cat, "Which keyword defines a function in Python?", 1, DifficultyLevel.EASY, "python", "def", "function", "fn", "lambda only");
        addMcq(cat, "What is a list comprehension?", 1, DifficultyLevel.EASY, "python", "Compact syntax to build lists", "A type of import", "GUI framework", "Debugger");
        addMcq(cat, "What does PEP 8 refer to?", 1, DifficultyLevel.MEDIUM, "python", "Style guide for Python code", "Package manager", "Async spec", "DB connector");
        addMcq(cat, "Which is immutable in Python?", 1, DifficultyLevel.MEDIUM, "python", "tuple (once created)", "list", "dict", "bytearray");
        addMcq(cat, "What does `if __name__ == \"__main__\"` guard?", 1, DifficultyLevel.MEDIUM, "python", "Runs code only when script executed directly", "Imports only", "Defines class", "Starts debugger");
        addMcq(cat, "GIL mainly affects what in CPython?", 1, DifficultyLevel.HARD, "python", "Parallel CPU threads for pure Python bytecode", "Network speed", "File encoding", "pip install speed");
        addMcq(cat, "Which is idiomatic for dependency management in modern Python projects?", 1, DifficultyLevel.EASY, "python", "pyproject.toml / poetry or pip-tools", "pom.xml", "build.gradle only", "Cargo.toml");
        return cat;
    }

    public static QuestionCategory sqlBasics() {
        QuestionCategory cat = cat("SQL_BASICS", "SQL & relational data", "SELECT, JOINs, keys, transactions — analytics and backends.");
        addMcq(cat, "Which clause filters rows before grouping?", 1, DifficultyLevel.EASY, "sql", "WHERE", "HAVING", "ORDER BY", "LIMIT only");
        addMcq(cat, "A PRIMARY KEY guarantees:", 1, DifficultyLevel.EASY, "sql", "Uniqueness and non-null (typically)", "Encryption", "Index removal", "Sharding only");
        addMcq(cat, "INNER JOIN returns rows when:", 1, DifficultyLevel.EASY, "sql", "Keys match in both tables", "Left table only", "Right table only", "No condition");
        addMcq(cat, "ACID property \"Atomicity\" means:", 1, DifficultyLevel.MEDIUM, "sql", "All operations in a transaction succeed or none do", "Parallel only", "Always fast", "No indexes");
        addMcq(cat, "What does `COUNT(*)` count?", 1, DifficultyLevel.EASY, "sql", "Rows (including nulls in columns)", "Distinct values only", "Columns", "Indexes");
        addMcq(cat, "A FOREIGN KEY enforces:", 1, DifficultyLevel.MEDIUM, "sql", "Referential integrity to another table", "Primary color", "File path", "GPU usage");
        addMcq(cat, "Index on a column usually speeds up:", 1, DifficultyLevel.MEDIUM, "sql", "Lookups and joins on that column", "INSERT always", "DROP DATABASE", "OS boot");
        addMcq(cat, "Normalization primarily reduces:", 1, DifficultyLevel.MEDIUM, "sql", "Redundancy and update anomalies", "Query speed always", "Security", "Disk size to zero");
        return cat;
    }

    public static QuestionCategory javascriptTypeScript() {
        QuestionCategory cat = cat(
                "JS_TS_WEB",
                "JavaScript & TypeScript",
                "ES modules, async, types — modern front and Node tooling.");
        addMcq(cat, "`const` declares:", 1, DifficultyLevel.EASY, "javascript", "A block-scoped binding that cannot be reassigned", "A global only", "A class", "A SQL table");
        addMcq(cat, "Promises are used to:", 1, DifficultyLevel.EASY, "javascript", "Handle asynchronous results", "Style CSS", "Compile Java", "Configure Docker");
        addMcq(cat, "TypeScript adds:", 1, DifficultyLevel.EASY, "typescript", "Static types (gradual) on top of JS", "A new browser", "Python runtime", "JVM");
        addMcq(cat, "`async/await` is syntactic sugar over:", 1, DifficultyLevel.MEDIUM, "javascript", "Promises", "Callbacks only without promises", "Threads", "XML");
        addMcq(cat, "Event loop in JS (browser/Node) means:", 1, DifficultyLevel.MEDIUM, "javascript", "Non-blocking I/O with a single thread model", "One CPU core only forever", "No HTTP", "No JSON");
        addMcq(cat, "Strict equality operator in JS is:", 1, DifficultyLevel.EASY, "javascript", "===", "== only", "=", "!==");
        addMcq(cat, "ES modules use which syntax?", 1, DifficultyLevel.EASY, "javascript", "import / export", "#include", "require only in browser", "package static");
        addMcq(cat, "Map vs plain object for keys:", 1, DifficultyLevel.MEDIUM, "javascript", "Map allows any key type and preserves insertion order (modern)", "No difference", "Object forbids strings", "Map is SQL only");
        return cat;
    }

    public static QuestionCategory reactWeb() {
        QuestionCategory cat = cat("REACT_WEB", "React & front components", "Hooks, state, JSX — component-driven UIs.");
        addMcq(cat, "useState returns:", 1, DifficultyLevel.EASY, "react", "A state value and a setter function", "Only a number", "A DOM node", "A router");
        addMcq(cat, "useEffect is for:", 1, DifficultyLevel.EASY, "react", "Side effects (fetch, subscriptions) tied to render lifecycle", "CSS only", "SQL queries on server", "Java compilation");
        addMcq(cat, "Virtual DOM helps React:", 1, DifficultyLevel.MEDIUM, "react", "Batch updates and minimize real DOM work", "Remove HTML", "Run Python", "Replace HTTP");
        addMcq(cat, "Keys in lists should be:", 1, DifficultyLevel.MEDIUM, "react", "Stable and unique among siblings", "Random every render", "Always index 0", "Global UUID from DB only");
        addMcq(cat, "Controlled input means:", 1, DifficultyLevel.MEDIUM, "react", "Value comes from React state", "Browser owns value only", "No onChange", "Read-only file system");
        addMcq(cat, "React.StrictMode in dev helps:", 1, DifficultyLevel.MEDIUM, "react", "Surface unsafe lifecycles / double-invoke effects", "Speed up prod", "Disable hooks", "Compile Java");
        addMcq(cat, "Fragment <> … </> avoids:", 1, DifficultyLevel.EASY, "react", "Extra DOM wrapper nodes", "All CSS", "State", "Props");
        return cat;
    }

    public static QuestionCategory springBoot() {
        QuestionCategory cat = cat(
                "SPRING_BOOT",
                "Spring Boot & APIs",
                "DI, REST controllers, configuration — JVM microservices.");
        addMcq(cat, "@RestController combines:", 1, DifficultyLevel.EASY, "spring", "@Controller + @ResponseBody", "@Entity only", "@Service only", "@Table");
        addMcq(cat, "Constructor injection is preferred because:", 1, DifficultyLevel.MEDIUM, "spring", "Immutable dependencies and easier testing", "Faster Python", "Smaller HTML", "No HTTP");
        addMcq(cat, "Default embedded server in Spring Boot is:", 1, DifficultyLevel.EASY, "spring", "Tomcat", "nginx only", "IIS", "Apache httpd only");
        addMcq(cat, "`application.yml` is:", 1, DifficultyLevel.EASY, "spring", "A common externalized configuration format", "Java source file", "React component", "Docker image");
        addMcq(cat, "@Transactional on a service method mainly manages:", 1, DifficultyLevel.MEDIUM, "spring", "Database transaction boundaries", "TCP ports", "CSS layout", "JWT signing only");
        addMcq(cat, "Spring Data JPA repositories reduce:", 1, DifficultyLevel.MEDIUM, "spring", "Boilerplate CRUD query code", "Need for SQL entirely always", "HTTP", "JSON");
        addMcq(cat, "Actuator endpoints expose:", 1, DifficultyLevel.MEDIUM, "spring", "Health, metrics, info for operations", "User passwords", "React bundle", "Git history");
        return cat;
    }

    public static QuestionCategory dockerKubernetes() {
        QuestionCategory cat = cat(
                "DOCKER_K8S",
                "Docker & Kubernetes",
                "Containers, images, pods — cloud-native delivery.");
        addMcq(cat, "A Docker image is:", 1, DifficultyLevel.EASY, "docker", "A read-only template of a filesystem + metadata", "A running process always", "A VM host OS", "A Git branch");
        addMcq(cat, "A container is:", 1, DifficultyLevel.EASY, "docker", "A runnable instance of an image", "Always persistent data by default", "Same as image always", "A DNS record");
        addMcq(cat, "Dockerfile purpose:", 1, DifficultyLevel.EASY, "docker", "Recipe to build an image", "Run Kubernetes", "Replace SQL", "Compile C# only");
        addMcq(cat, "In Kubernetes, a Pod is:", 1, DifficultyLevel.EASY, "k8s", "The smallest deployable unit (often one main container)", "A physical server only", "A database row", "A CSS file");
        addMcq(cat, "Deployment controller ensures:", 1, DifficultyLevel.MEDIUM, "k8s", "Desired replica count of pods", "Disk encryption at rest always", "Compile React", "SSH keys");
        addMcq(cat, "ConfigMap holds:", 1, DifficultyLevel.MEDIUM, "k8s", "Non-secret configuration data", "Passwords plaintext always", "Docker Hub login only", "GPU drivers");
        addMcq(cat, "kubectl apply applies:", 1, DifficultyLevel.MEDIUM, "k8s", "Declarative manifests to the cluster", "Only SQL migrations", "npm install", "Git merge");
        return cat;
    }

    public static QuestionCategory awsCloud() {
        QuestionCategory cat = cat("AWS_CLOUD", "AWS cloud essentials", "Compute, storage, IAM — common interview topics.");
        addMcq(cat, "S3 is primarily:", 1, DifficultyLevel.EASY, "aws", "Object storage", "Relational DB", "Message queue", "Kubernetes control plane");
        addMcq(cat, "IAM user credentials should:", 1, DifficultyLevel.EASY, "aws", "Follow least privilege", "Use root for daily tasks", "Be shared in Slack", "Be committed to Git");
        addMcq(cat, "EC2 is:", 1, DifficultyLevel.EASY, "aws", "Virtual machines in AWS", "Serverless functions only", "DNS only", "CDN only");
        addMcq(cat, "VPC provides:", 1, DifficultyLevel.MEDIUM, "aws", "Isolated network for your resources", "Free SSL for all domains globally", "Managed React", "SQL dialect");
        addMcq(cat, "Lambda is:", 1, DifficultyLevel.EASY, "aws", "Function-as-a-service (event-driven compute)", "Always-on VM", "S3 bucket type", "IAM policy syntax");
        addMcq(cat, "CloudWatch is used for:", 1, DifficultyLevel.MEDIUM, "aws", "Metrics, logs, alarms", "Source code hosting", "Container build only", "Kubernetes only");
        addMcq(cat, "RDS is:", 1, DifficultyLevel.MEDIUM, "aws", "Managed relational database service", "Object storage", "DNS", "CDN");
        return cat;
    }

    public static QuestionCategory gitWorkflow() {
        QuestionCategory cat = cat("GIT_WORKFLOW", "Git & collaboration", "Branches, merges, PRs — daily engineering workflow.");
        addMcq(cat, "`git clone` copies:", 1, DifficultyLevel.EASY, "git", "Remote repository to local", "Only one file", "Database", "Docker layers only");
        addMcq(cat, "A commit records:", 1, DifficultyLevel.EASY, "git", "A snapshot of staged changes with metadata", "Only comments", "Network packet", "CPU temperature");
        addMcq(cat, "Merge vs rebase (simplified): merge", 1, DifficultyLevel.MEDIUM, "git", "Creates a merge commit combining histories", "Deletes history always", "Only works on SVN", "Removes branches");
        addMcq(cat, ".gitignore is for:", 1, DifficultyLevel.EASY, "git", "Patterns of files Git should not track", "Ignoring users", "SSL certs only", "SQL indexes");
        addMcq(cat, "Pull request (PR) is:", 1, DifficultyLevel.EASY, "git", "A request to merge a branch after review", "A SQL SELECT", "A Docker push", "AWS bill");
        addMcq(cat, "`git stash` temporarily:", 1, DifficultyLevel.MEDIUM, "git", "Shelves working directory changes", "Deletes repo", "Pushes to main", "Creates tag");
        addMcq(cat, "Fast-forward merge possible when:", 1, DifficultyLevel.MEDIUM, "git", "Target branch has no new commits since branch point", "Always", "Never", "Only with rebase");
        return cat;
    }

    public static QuestionCategory restApiDesign() {
        QuestionCategory cat = cat("REST_API", "REST & HTTP APIs", "Verbs, status codes, idempotency — API design.");
        addMcq(cat, "HTTP GET should be:", 1, DifficultyLevel.EASY, "http", "Safe and idempotent (no side effects expected)", "Always creating resources", "Always deleting", "GraphQL only");
        addMcq(cat, "201 Created usually means:", 1, DifficultyLevel.EASY, "http", "Resource was created", "Validation error", "Unauthorized", "Rate limited");
        addMcq(cat, "401 vs 403 (typical):", 1, DifficultyLevel.MEDIUM, "http", "401 not authenticated; 403 authenticated but forbidden", "Same thing", "401 server down", "403 means not found");
        addMcq(cat, "Idempotent methods include:", 1, DifficultyLevel.MEDIUM, "http", "GET, PUT, DELETE (intended semantics)", "POST always", "PATCH always", "CONNECT always");
        addMcq(cat, "HATEOAS in REST refers to:", 1, DifficultyLevel.HARD, "http", "Hypermedia links in responses", "Faster JSON", "HTTPS only", "SOAP only");
        addMcq(cat, "API versioning strategies include:", 1, DifficultyLevel.MEDIUM, "http", "URL path, headers, or query params", "Only cookies", "Only SSH", "DNS TXT only");
        addMcq(cat, "OpenAPI (Swagger) describes:", 1, DifficultyLevel.MEDIUM, "http", "Machine-readable API contract", "Linux kernel", "React state", "SQL optimizer");
        return cat;
    }

    public static QuestionCategory webSecurity() {
        QuestionCategory cat = cat(
                "WEB_SECURITY",
                "Web security basics",
                "XSS, CSRF, HTTPS, OWASP — shipping safer apps.");
        addMcq(cat, "XSS stands for:", 1, DifficultyLevel.EASY, "security", "Cross-site scripting", "XML secure socket", "Extra SQL server", "Xen server sync");
        addMcq(cat, "CSRF protection often uses:", 1, DifficultyLevel.MEDIUM, "security", "Anti-forgery tokens tied to session", "Longer passwords only", "Disabling HTTPS", "Removing cookies");
        addMcq(cat, "HTTPS primarily provides:", 1, DifficultyLevel.EASY, "security", "Encryption and integrity on the wire", "Faster CPU", "Free hosting", "SQL joins");
        addMcq(cat, "Hashing passwords should use:", 1, DifficultyLevel.MEDIUM, "security", "Slow adaptive algorithms (e.g. bcrypt, Argon2)", "MD5 alone", "Base64 alone", "Plaintext");
        addMcq(cat, "Content Security Policy (CSP) reduces:", 1, DifficultyLevel.MEDIUM, "security", "Risk of malicious script execution", "Need for HTML", "REST", "Git");
        addMcq(cat, "OWASP Top 10 is:", 1, DifficultyLevel.EASY, "security", "A widely used web app risk awareness list", "A Java package", "AWS service", "React hook");
        addMcq(cat, "JWTs are often used for:", 1, DifficultyLevel.MEDIUM, "security", "Stateless bearer tokens (with caveats)", "Storing credit cards in browser", "Replacing TLS", "SQL transactions");
        return cat;
    }

    public static QuestionCategory dsaBasics() {
        QuestionCategory cat = cat("DSA_BASICS", "Data structures & algorithms", "Complexity, stacks, trees — coding interviews.");
        addMcq(cat, "Big-O describes:", 1, DifficultyLevel.EASY, "dsa", "How resource usage scales with input size", "Exact milliseconds on one PC", "Memory brand", "SQL dialect");
        addMcq(cat, "A stack is:", 1, DifficultyLevel.EASY, "dsa", "LIFO", "FIFO always", "Random access primary", "Sorted tree only");
        addMcq(cat, "A queue is:", 1, DifficultyLevel.EASY, "dsa", "FIFO", "LIFO", "Key-value only", "Bitmap only");
        addMcq(cat, "Binary search requires:", 1, DifficultyLevel.MEDIUM, "dsa", "Sorted random-access collection", "Unsorted always", "Linked list only", "Graph only");
        addMcq(cat, "Hash map average lookup:", 1, DifficultyLevel.MEDIUM, "dsa", "O(1) average (good hash, low collisions)", "O(n) always", "O(log n) always", "O(1) worst always");
        addMcq(cat, "DFS vs BFS on graphs:", 1, DifficultyLevel.MEDIUM, "dsa", "DFS goes deep first; BFS explores level by level", "Identical always", "Only for SQL", "Only on trees never graphs");
        addMcq(cat, "Dynamic programming often trades:", 1, DifficultyLevel.HARD, "dsa", "Time vs space via memoization / tabulation", "CPU vs monitor", "HTTP vs FTP", "Git vs SVN");
        return cat;
    }

    public static QuestionCategory genAiLlm() {
        QuestionCategory cat = cat(
                "GENAI_LLM",
                "LLM & generative AI",
                "Transformers, tokens, prompts — trending AI engineering.");
        addMcq(cat, "LLM usually means:", 1, DifficultyLevel.EASY, "llm", "Large Language Model", "Low-latency ML", "Linked list manager", "Local log module");
        addMcq(cat, "Transformer attention helps:", 1, DifficultyLevel.MEDIUM, "llm", "Relate tokens across the sequence", "Sort arrays only", "Compile Java", "Run Docker");
        addMcq(cat, "Temperature ↑ often:", 1, DifficultyLevel.MEDIUM, "llm", "Increases output randomness", "Makes deterministic outputs", "Reduces tokens", "Disables GPU");
        addMcq(cat, "RAG augments LLMs with:", 1, DifficultyLevel.MEDIUM, "llm", "Retrieved documents/context", "More GPUs only", "Smaller monitors", "FTP");
        addMcq(cat, "Fine-tuning updates:", 1, DifficultyLevel.MEDIUM, "llm", "Model weights on domain data", "Only CSS", "DNS records", "Git config");
        addMcq(cat, "Prompt injection is:", 1, DifficultyLevel.MEDIUM, "llm", "Malicious user input overriding system intent", "A SQL join", "HTTPS error", "CPU overclock");
        addMcq(cat, "Embeddings map text to:", 1, DifficultyLevel.MEDIUM, "llm", "Vectors for similarity / retrieval", "JPEG images", "SQL rows only", "Binary executables");
        return cat;
    }

    public static QuestionCategory nodeBackend() {
        QuestionCategory cat = cat("NODE_BACKEND", "Node.js backend", "Event loop, npm, Express-style APIs.");
        addMcq(cat, "Node.js is built on:", 1, DifficultyLevel.EASY, "node", "V8 JavaScript engine (typically)", "JVM", "CPython VM", ".NET CLR");
        addMcq(cat, "require / import in Node loads:", 1, DifficultyLevel.EASY, "node", "Modules (local or package)", "SQL only", "GPU drivers", "Kubernetes pods");
        addMcq(cat, "Non-blocking I/O means:", 1, DifficultyLevel.MEDIUM, "node", "While waiting on I/O, other work can proceed", "No network", "Single instruction only", "No async");
        addMcq(cat, "package.json declares:", 1, DifficultyLevel.EASY, "node", "Project metadata and dependencies", "Dockerfile only", "Java classpath", "MySQL schema");
        addMcq(cat, "npm ci is suited for:", 1, DifficultyLevel.MEDIUM, "node", "Clean installs from lockfile in CI", "Interactive dev only", "Publishing without login", "Running Python");
        addMcq(cat, "Cluster module can:", 1, DifficultyLevel.HARD, "node", "Spread work across CPU cores (processes)", "Replace Kubernetes", "Compile TypeScript without tsc", "Manage React state");
        addMcq(cat, "Streams in Node help with:", 1, DifficultyLevel.MEDIUM, "node", "Processing large data incrementally", "CSS animations", "SQL transactions only", "Git merge");
        return cat;
    }

    public static QuestionCategory mongodbNoSql() {
        QuestionCategory cat = cat(
                "MONGODB_NOSQL",
                "MongoDB & document stores",
                "Documents, collections, flexible schema — NoSQL patterns.");
        addMcq(cat, "MongoDB stores documents in:", 1, DifficultyLevel.EASY, "mongo", "BSON (binary JSON-like)", "Relational tables only", "CSV only", "Graph nodes only");
        addMcq(cat, "A collection is analogous to:", 1, DifficultyLevel.EASY, "mongo", "A table (loosely) in SQL terms", "A row", "A column", "An index only");
        addMcq(cat, "_id field is:", 1, DifficultyLevel.EASY, "mongo", "Primary key-like unique identifier per document", "Always UUID string", "Optional always", "Foreign key only");
        addMcq(cat, "Aggregation pipeline is for:", 1, DifficultyLevel.MEDIUM, "mongo", "Data transformation and analytics in-database", "Only inserts", "Only backups", "SSL termination");
        addMcq(cat, "Indexes in MongoDB speed up:", 1, DifficultyLevel.MEDIUM, "mongo", "Queries and sorts that match index keys", "All writes always", "Network ping", "React renders");
        addMcq(cat, "Replica set provides:", 1, DifficultyLevel.MEDIUM, "mongo", "High availability via redundant nodes", "Free hosting", "SQL joins", "GraphQL only");
        addMcq(cat, "Sharding is used to:", 1, DifficultyLevel.HARD, "mongo", "Horizontally partition data across clusters", "Encrypt disks", "Compile JS", "Run Kubernetes inside DB");
        return cat;
    }

    public static QuestionCategory angularFront() {
        QuestionCategory cat = cat(
                "ANGULAR_WEB",
                "Angular framework",
                "Components, DI, RxJS — enterprise SPAs.");
        addMcq(cat, "Angular services use DI to:", 1, DifficultyLevel.EASY, "angular", "Provide reusable logic across components", "Style only", "Replace HTTP", "Compile Python");
        addMcq(cat, "NgModule vs standalone (modern):", 1, DifficultyLevel.MEDIUM, "angular", "Standalone components reduce NgModule boilerplate", "NgModule is only way", "Both banned", "Only for mobile");
        addMcq(cat, "AsyncPipe subscribes to:", 1, DifficultyLevel.MEDIUM, "angular", "Observables/Promises in templates safely", "DOM events only", "SQL", "Git");
        addMcq(cat, "Change detection strategies include:", 1, DifficultyLevel.MEDIUM, "angular", "Default and OnPush (performance)", "Only OnPush forever", "No strategies", "CSS only");
        addMcq(cat, "Router lazy loading loads:", 1, DifficultyLevel.MEDIUM, "angular", "Feature modules on demand", "All images", "MySQL", "Docker images");
        addMcq(cat, "HttpClient returns:", 1, DifficultyLevel.EASY, "angular", "Observables of responses (by default)", "Callbacks only", "DOM nodes", "SQL cursors");
        addMcq(cat, "Zone.js (classic) helps Angular:", 1, DifficultyLevel.HARD, "angular", "Know when async work finishes to run CD", "Compile SASS only", "Run Kubernetes", "Replace RxJS");
        return cat;
    }

    public static QuestionCategory linuxShell() {
        QuestionCategory cat = cat(
                "LINUX_SHELL",
                "Linux & shell",
                "Permissions, processes, pipes — servers and CI.");
        addMcq(cat, "`chmod +x` affects:", 1, DifficultyLevel.EASY, "linux", "Execute permission on a file", "Copy speed", "RAM size", "DNS");
        addMcq(cat, "`grep` searches:", 1, DifficultyLevel.EASY, "linux", "Text patterns in input", "Packages to install", "GPU model", "SSL certs");
        addMcq(cat, "Pipe `|` sends:", 1, DifficultyLevel.EASY, "linux", "stdout of left command to stdin of right", "UDP packets", "SQL rows", "Git objects");
        addMcq(cat, "`ps aux` lists:", 1, DifficultyLevel.EASY, "linux", "Processes", "Open ports only", "Disk partitions only", "Users only");
        addMcq(cat, "`systemctl` manages:", 1, DifficultyLevel.MEDIUM, "linux", "systemd services", "npm packages", "Kubernetes pods only", "React state");
        addMcq(cat, "SSH keys authenticate via:", 1, DifficultyLevel.MEDIUM, "linux", "Public/private key pairs", "Password in URL", "SQL role", "JWT in cookies only");
        addMcq(cat, "`curl` is used to:", 1, DifficultyLevel.EASY, "linux", "Transfer data with URLs (HTTP, etc.)", "Compile C", "Run Docker only", "Format USB");
        return cat;
    }

    public static QuestionCategory microservicesPatterns() {
        QuestionCategory cat = cat(
                "MICROSERVICES",
                "Microservices & distributed systems",
                "Timeouts, sagas, discovery — system design interviews.");
        addMcq(cat, "Circuit breaker pattern helps:", 1, DifficultyLevel.MEDIUM, "microservices", "Fail fast when downstream is unhealthy", "Increase latency always", "Remove HTTP", "Compile faster");
        addMcq(cat, "API gateway often provides:", 1, DifficultyLevel.MEDIUM, "microservices", "Auth, routing, rate limiting to services", "Database storage", "React rendering", "Git hosting");
        addMcq(cat, "Idempotent retry needs:", 1, DifficultyLevel.HARD, "microservices", "Safe operations or deduplication keys", "Always POST", "No network", "Single thread only");
        addMcq(cat, "Saga pattern coordinates:", 1, DifficultyLevel.HARD, "microservices", "Distributed transactions via compensating actions", "CPU overclock", "CSS grid", "SQL views only");
        addMcq(cat, "Service discovery lets:", 1, DifficultyLevel.MEDIUM, "microservices", "Clients find instances dynamically", "Compile Java faster", "Encrypt disks", "Run without OS");
        addMcq(cat, "Backpressure in streams means:", 1, DifficultyLevel.HARD, "microservices", "Slowing producers when consumers lag", "Increasing RAM", "Deleting logs", "SQL index");
        addMcq(cat, "Distributed tracing (e.g. OpenTelemetry) helps:", 1, DifficultyLevel.MEDIUM, "microservices", "Follow requests across services", "Style buttons", "Replace REST", "Compile TypeScript");
        return cat;
    }

    private static QuestionCategory cat(String code, String title, String description) {
        return QuestionCategory.builder().code(code).title(title).description(description).build();
    }

    private static void add(
            QuestionCategory cat,
            String prompt,
            int points,
            DifficultyLevel difficulty,
            String topic,
            String correct,
            String w1,
            String w2,
            String w3) {
        Question q = Question.builder()
                .category(cat)
                .prompt(prompt)
                .points(points)
                .difficulty(difficulty)
                .active(true)
                .topic(topic)
                .build();
        q.getChoices().add(AnswerChoice.builder().question(q).label(correct).correct(true).sortOrder(1).build());
        q.getChoices().add(AnswerChoice.builder().question(q).label(w1).correct(false).sortOrder(2).build());
        q.getChoices().add(AnswerChoice.builder().question(q).label(w2).correct(false).sortOrder(3).build());
        q.getChoices().add(AnswerChoice.builder().question(q).label(w3).correct(false).sortOrder(4).build());
        cat.getQuestions().add(q);
    }

    private static void addMcq(
            QuestionCategory cat,
            String prompt,
            int points,
            DifficultyLevel difficulty,
            String topic,
            String correct,
            String w1,
            String w2,
            String w3) {
        add(cat, prompt, points, difficulty, topic, correct, w1, w2, w3);
    }

    // ── New Categories for Enhanced Suggestions ────────────────────────────────

    public static QuestionCategory cSharpDotNet() {
        QuestionCategory cat = cat("CSHARP_DOTNET", "C# & .NET", "OOP, LINQ, ASP.NET — Microsoft stack development.");
        addMcq(cat, "C# is primarily:", 1, DifficultyLevel.EASY, "csharp", "Object-oriented with strong typing", "Functional only", "Assembly language", "Markup language");
        addMcq(cat, "LINQ stands for:", 1, DifficultyLevel.EASY, "csharp", "Language Integrated Query", "Linear Query", "Logic Query", "List Query");
        addMcq(cat, "ASP.NET Core is:", 1, DifficultyLevel.MEDIUM, "csharp", "Cross-platform web framework", "Windows only", "Database only", "CSS framework");
        addMcq(cat, "Entity Framework is:", 1, DifficultyLevel.MEDIUM, "csharp", "ORM for .NET", "Web server", "CSS library", "Testing framework");
        return cat;
    }

    public static QuestionCategory phpWeb() {
        QuestionCategory cat = cat("PHP_WEB", "PHP & Web Development", "Server-side scripting, Laravel, WordPress — web backends.");
        addMcq(cat, "PHP is primarily used for:", 1, DifficultyLevel.EASY, "php", "Server-side web development", "Mobile apps only", "Desktop GUI", "Machine learning only");
        addMcq(cat, "Laravel is:", 1, DifficultyLevel.EASY, "php", "PHP web framework", "Database", "CSS library", "JavaScript runtime");
        addMcq(cat, "Composer is:", 1, DifficultyLevel.MEDIUM, "php", "PHP dependency manager", "Web server", "Database", "CSS preprocessor");
        addMcq(cat, "PHP variables start with:", 1, DifficultyLevel.EASY, "php", "$", "@", "#", "%");
        return cat;
    }

    public static QuestionCategory rubyRails() {
        QuestionCategory cat = cat("RUBY_RAILS", "Ruby & Rails", "Convention over configuration — rapid web development.");
        addMcq(cat, "Ruby on Rails follows:", 1, DifficultyLevel.EASY, "ruby", "Convention over Configuration", "Configuration over Convention", "No conventions", "Manual setup only");
        addMcq(cat, "Rails MVC stands for:", 1, DifficultyLevel.EASY, "ruby", "Model-View-Controller", "Model-View-Component", "Module-View-Class", "Method-Variable-Class");
        addMcq(cat, "Gems in Ruby are:", 1, DifficultyLevel.EASY, "ruby", "Packages/libraries", "Variables", "Functions", "Classes only");
        addMcq(cat, "ActiveRecord is:", 1, DifficultyLevel.MEDIUM, "ruby", "Rails ORM pattern", "Web server", "CSS framework", "Testing tool");
        return cat;
    }

    public static QuestionCategory goLang() {
        QuestionCategory cat = cat("GO_LANG", "Go Programming", "Concurrency, simplicity — cloud-native backends.");
        addMcq(cat, "Go was created by:", 1, DifficultyLevel.EASY, "go", "Google", "Microsoft", "Apple", "Facebook");
        addMcq(cat, "Goroutines are:", 1, DifficultyLevel.MEDIUM, "go", "Lightweight threads managed by Go runtime", "Heavy OS threads", "Functions only", "Variables");
        addMcq(cat, "Go compilation produces:", 1, DifficultyLevel.EASY, "go", "Static binaries", "Bytecode only", "Interpreted scripts", "Dynamic libraries only");
        addMcq(cat, "Channels in Go are for:", 1, DifficultyLevel.MEDIUM, "go", "Communication between goroutines", "File I/O only", "HTTP requests", "CSS styling");
        return cat;
    }

    public static QuestionCategory rustSystems() {
        QuestionCategory cat = cat("RUST_SYSTEMS", "Rust Systems Programming", "Memory safety, performance — systems without garbage collection.");
        addMcq(cat, "Rust's main selling point is:", 1, DifficultyLevel.EASY, "rust", "Memory safety without garbage collection", "Fastest compilation", "Largest ecosystem", "Easiest syntax");
        addMcq(cat, "Ownership in Rust prevents:", 1, DifficultyLevel.MEDIUM, "rust", "Memory leaks and data races", "All errors", "Compilation", "Network access");
        addMcq(cat, "Cargo is:", 1, DifficultyLevel.EASY, "rust", "Rust's package manager and build tool", "Web framework", "Database", "Text editor");
        addMcq(cat, "Rust is often used for:", 1, DifficultyLevel.MEDIUM, "rust", "Systems programming and WebAssembly", "Web frontend only", "Mobile apps only", "Databases only");
        return cat;
    }

    public static QuestionCategory kotlinAndroid() {
        QuestionCategory cat = cat("KOTLIN_ANDROID", "Kotlin & Android", "Mobile development, coroutines — modern Android apps.");
        addMcq(cat, "Kotlin is:", 1, DifficultyLevel.EASY, "kotlin", "JVM language, fully interoperable with Java", "JavaScript only", "C++ replacement", "Assembly language");
        addMcq(cat, "Android officially supports:", 1, DifficultyLevel.EASY, "kotlin", "Kotlin as first-class language", "Java only", "C++ only", "Python only");
        addMcq(cat, "Coroutines in Kotlin handle:", 1, DifficultyLevel.MEDIUM, "kotlin", "Asynchronous programming", "UI layout only", "Database queries only", "File compression");
        addMcq(cat, "Jetpack Compose is:", 1, DifficultyLevel.MEDIUM, "kotlin", "Modern Android UI toolkit", "Database ORM", "Network library", "Testing framework");
        return cat;
    }

    public static QuestionCategory swiftIos() {
        QuestionCategory cat = cat("SWIFT_IOS", "Swift & iOS", "Apple ecosystem development — iPhone and iPad apps.");
        addMcq(cat, "Swift was created by:", 1, DifficultyLevel.EASY, "swift", "Apple", "Google", "Microsoft", "Facebook");
        addMcq(cat, "SwiftUI is:", 1, DifficultyLevel.EASY, "swift", "Declarative UI framework for Apple platforms", "Database", "Web server", "Testing tool");
        addMcq(cat, "Optionals in Swift handle:", 1, DifficultyLevel.MEDIUM, "swift", "Null safety", "Memory management only", "Network requests", "File I/O");
        addMcq(cat, "Xcode is:", 1, DifficultyLevel.EASY, "swift", "Apple's IDE for iOS/macOS development", "Text editor only", "Database tool", "Web browser");
        return cat;
    }

    public static QuestionCategory flutterDart() {
        QuestionCategory cat = cat("FLUTTER_DART", "Flutter & Dart", "Cross-platform mobile — single codebase for iOS and Android.");
        addMcq(cat, "Flutter is developed by:", 1, DifficultyLevel.EASY, "flutter", "Google", "Apple", "Microsoft", "Facebook");
        addMcq(cat, "Flutter apps are written in:", 1, DifficultyLevel.EASY, "flutter", "Dart", "JavaScript", "Java", "Swift");
        addMcq(cat, "Flutter's main advantage is:", 1, DifficultyLevel.EASY, "flutter", "Single codebase for multiple platforms", "Fastest performance always", "Smallest app size", "No learning curve");
        addMcq(cat, "Widgets in Flutter are:", 1, DifficultyLevel.MEDIUM, "flutter", "Building blocks of the UI", "Database tables", "Network protocols", "File formats");
        return cat;
    }

    public static QuestionCategory vueJs() {
        QuestionCategory cat = cat("VUE_JS", "Vue.js Framework", "Progressive JavaScript framework — approachable frontend development.");
        addMcq(cat, "Vue.js is:", 1, DifficultyLevel.EASY, "vue", "Progressive JavaScript framework", "Backend framework", "Database", "CSS preprocessor");
        addMcq(cat, "Vue's template syntax uses:", 1, DifficultyLevel.EASY, "vue", "HTML-based templates with directives", "JSX only", "Pure JavaScript", "CSS only");
        addMcq(cat, "Vuex is:", 1, DifficultyLevel.MEDIUM, "vue", "State management library for Vue", "CSS framework", "Testing tool", "Build tool");
        addMcq(cat, "Vue CLI helps with:", 1, DifficultyLevel.MEDIUM, "vue", "Project scaffolding and build tools", "Database queries", "Server deployment", "CSS styling");
        return cat;
    }

    public static QuestionCategory machineLearning() {
        QuestionCategory cat = cat("MACHINE_LEARNING", "Machine Learning", "Algorithms, models, training — AI and data science.");
        addMcq(cat, "Supervised learning uses:", 1, DifficultyLevel.EASY, "ml", "Labeled training data", "No data", "Unlabeled data only", "Random data");
        addMcq(cat, "Overfitting means:", 1, DifficultyLevel.MEDIUM, "ml", "Model memorizes training data but generalizes poorly", "Model is too simple", "Model trains too fast", "Model uses too little data");
        addMcq(cat, "Cross-validation helps:", 1, DifficultyLevel.MEDIUM, "ml", "Assess model performance on unseen data", "Speed up training", "Reduce data size", "Increase accuracy always");
        addMcq(cat, "Feature engineering involves:", 1, DifficultyLevel.MEDIUM, "ml", "Selecting and transforming input variables", "Training models only", "Collecting data only", "Deploying models only");
        return cat;
    }

    public static QuestionCategory dataScience() {
        QuestionCategory cat = cat("DATA_SCIENCE", "Data Science", "Analytics, visualization, statistics — insights from data.");
        addMcq(cat, "Pandas is used for:", 1, DifficultyLevel.EASY, "datascience", "Data manipulation and analysis in Python", "Web development", "Game development", "Mobile apps");
        addMcq(cat, "A/B testing is:", 1, DifficultyLevel.MEDIUM, "datascience", "Comparing two versions to determine which performs better", "Database backup", "Code review", "UI design");
        addMcq(cat, "Statistical significance indicates:", 1, DifficultyLevel.MEDIUM, "datascience", "Results are unlikely due to chance", "Results are always correct", "Data is clean", "Model is perfect");
        addMcq(cat, "Data visualization helps:", 1, DifficultyLevel.EASY, "datascience", "Communicate insights and patterns", "Store data", "Clean data only", "Collect data");
        return cat;
    }

    public static QuestionCategory cybersecurity() {
        QuestionCategory cat = cat("CYBERSECURITY", "Cybersecurity", "Threats, defense, compliance — protecting digital assets.");
        addMcq(cat, "Penetration testing is:", 1, DifficultyLevel.EASY, "security", "Authorized simulated cyber attack", "Network monitoring", "Data backup", "Code review");
        addMcq(cat, "Zero-day vulnerability is:", 1, DifficultyLevel.MEDIUM, "security", "Unknown security flaw with no available patch", "Old vulnerability", "Fixed vulnerability", "Fake vulnerability");
        addMcq(cat, "Multi-factor authentication adds:", 1, DifficultyLevel.EASY, "security", "Additional security layers beyond passwords", "Complexity only", "Speed", "Storage");
        addMcq(cat, "Social engineering targets:", 1, DifficultyLevel.MEDIUM, "security", "Human psychology to gain unauthorized access", "Computer hardware", "Network protocols", "Database schemas");
        return cat;
    }

    public static QuestionCategory devopsAdvanced() {
        QuestionCategory cat = cat("DEVOPS_ADVANCED", "Advanced DevOps", "CI/CD, monitoring, infrastructure as code — operational excellence.");
        addMcq(cat, "Infrastructure as Code means:", 1, DifficultyLevel.MEDIUM, "devops", "Managing infrastructure through code and version control", "Manual server setup", "GUI-only configuration", "Hardware programming");
        addMcq(cat, "Blue-green deployment:", 1, DifficultyLevel.MEDIUM, "devops", "Maintains two identical production environments", "Uses only blue servers", "Requires green cables", "Is a testing strategy only");
        addMcq(cat, "Monitoring vs Observability:", 1, DifficultyLevel.HARD, "devops", "Observability provides deeper insights into system behavior", "They are identical", "Monitoring is newer", "Observability is simpler");
        addMcq(cat, "GitOps deploys by:", 1, DifficultyLevel.MEDIUM, "devops", "Using Git as source of truth for infrastructure", "Manual deployment only", "FTP uploads", "Email notifications");
        return cat;
    }

    public static QuestionCategory blockchainWeb3() {
        QuestionCategory cat = cat("BLOCKCHAIN_WEB3", "Blockchain & Web3", "Decentralized applications, smart contracts — distributed systems.");
        addMcq(cat, "Blockchain is:", 1, DifficultyLevel.EASY, "blockchain", "Distributed ledger technology", "Centralized database", "Web framework", "CSS library");
        addMcq(cat, "Smart contracts are:", 1, DifficultyLevel.MEDIUM, "blockchain", "Self-executing contracts with terms in code", "Legal documents only", "Database queries", "Web APIs");
        addMcq(cat, "Ethereum is:", 1, DifficultyLevel.EASY, "blockchain", "Blockchain platform for smart contracts", "Cryptocurrency only", "Web browser", "Database system");
        addMcq(cat, "DeFi stands for:", 1, DifficultyLevel.EASY, "blockchain", "Decentralized Finance", "Digital Finance", "Direct Finance", "Distributed Finance");
        return cat;
    }

    public static QuestionCategory gameDevUnity() {
        QuestionCategory cat = cat("GAME_DEV_UNITY", "Game Development", "Unity, game engines, interactive entertainment.");
        addMcq(cat, "Unity uses which language primarily:", 1, DifficultyLevel.EASY, "gamedev", "C#", "Java", "Python", "JavaScript");
        addMcq(cat, "Game loop typically includes:", 1, DifficultyLevel.MEDIUM, "gamedev", "Update and Render phases", "Compile and Deploy", "Read and Write", "Start and Stop");
        addMcq(cat, "Collision detection determines:", 1, DifficultyLevel.MEDIUM, "gamedev", "When game objects intersect", "Graphics quality", "Sound volume", "Network latency");
        addMcq(cat, "Frame rate affects:", 1, DifficultyLevel.EASY, "gamedev", "Smoothness of animation", "File size only", "Code complexity", "Network speed");
        return cat;
    }

    public static QuestionCategory uxUiDesign() {
        QuestionCategory cat = cat("UX_UI_DESIGN", "UX/UI Design", "User experience, interface design — human-centered design.");
        addMcq(cat, "UX focuses on:", 1, DifficultyLevel.EASY, "ux", "Overall user experience and usability", "Visual appearance only", "Code quality", "Server performance");
        addMcq(cat, "UI focuses on:", 1, DifficultyLevel.EASY, "ui", "Visual interface and interaction design", "Database design", "Server architecture", "Network protocols");
        addMcq(cat, "User personas help:", 1, DifficultyLevel.MEDIUM, "ux", "Understand target user needs and behaviors", "Write code faster", "Reduce server costs", "Improve database performance");
        addMcq(cat, "Wireframes are:", 1, DifficultyLevel.EASY, "ux", "Low-fidelity structural blueprints", "High-resolution images", "Database schemas", "Network diagrams");
        return cat;
    }
}
