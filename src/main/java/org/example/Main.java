package org.example;
// tag::import[]
import com.vaticle.typedb.driver.TypeDB;
import com.vaticle.typedb.driver.api.TypeDBDriver;
import com.vaticle.typedb.driver.api.TypeDBOptions;
import com.vaticle.typedb.driver.api.TypeDBSession;
import com.vaticle.typedb.driver.api.TypeDBTransaction;
import com.vaticle.typedb.driver.api.concept.Concept;
import com.vaticle.typedb.driver.api.concept.thing.Attribute;
import com.vaticle.typedb.driver.api.concept.thing.Entity;
import com.vaticle.typedb.driver.api.concept.type.AttributeType;
import com.vaticle.typedb.driver.api.concept.type.EntityType;
import com.vaticle.typedb.driver.api.concept.type.ThingType;
import com.vaticle.typedb.driver.api.concept.value.Value;
import com.vaticle.typedb.driver.api.logic.Rule;
import com.vaticle.typeql.lang.TypeQL;
import com.vaticle.typeql.lang.pattern.Pattern;


import java.util.Collections;
import java.util.Set;


// end::import[]
public class Main {
    public static void main(String[] args) {
        final String DB_NAME = "sample_db";
        final String SERVER_ADDR = "127.0.0.1:1729";

        System.out.println("TypeDB Manual sample code");
        // tag::driver[]
        TypeDBDriver driver = TypeDB.coreDriver(SERVER_ADDR);
        // end::driver[]
        // tag::list-db[]
        driver.databases().all().forEach( result -> System.out.println(result.name()));
        // end::list-db[]
        // tag::delete-db[]
        if (driver.databases().contains(DB_NAME)) {
            driver.databases().get(DB_NAME).delete();
        }
        // end::delete-db[]
        // tag::create-db[]
        driver.databases().create(DB_NAME);
        // end::create-db[]
        if (driver.databases().contains(DB_NAME)) {
            System.out.println("Database setup complete.");
        }
        // tag::define[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String defineQuery = """
                                    define
                                    email sub attribute, value string;
                                    name sub attribute, value string;
                                    friendship sub relation, relates friend;
                                    user sub entity,
                                        owns email @key,
                                        owns name,
                                        plays friendship:friend;
                                    admin sub user;
                                    """;
                transaction.query().define(defineQuery).resolve();
                transaction.commit();
            }
        }
        // end::define[]
        // tag::undefine[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String undefineQuery = "undefine admin sub user;";
                transaction.query().undefine(undefineQuery).resolve();
                transaction.commit();
            }
        }
        // end::undefine[]
        // tag::insert[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String insertQuery = """
                                    insert
                                    $user1 isa user, has name "Alice", has email "alice@vaticle.com";
                                    $user2 isa user, has name "Bob", has email "bob@vaticle.com";
                                    $friendship (friend:$user1, friend: $user2) isa friendship;
                                    """;
                transaction.query().insert(insertQuery);
                transaction.commit();
            }
        }
        // end::insert[]
        // tag::match-insert[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String matchInsertQuery = """
                                        match
                                        $u isa user, has name "Bob";
                                        insert
                                        $new-u isa user, has name "Charlie", has email "charlie@vaticle.com";
                                        $f($u,$new-u) isa friendship;
                                        """;
                long response_count = transaction.query().insert(matchInsertQuery).count();
                if (response_count == 1) {
                    transaction.commit();
                } else {
                    transaction.close();
                }
            }
        }
        // end::match-insert[]
        // tag::delete[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String deleteQuery = """
                                    match
                                    $u isa user, has name "Charlie";
                                    $f ($u) isa friendship;
                                    delete
                                    $f isa friendship;
                                    """;
                transaction.query().delete(deleteQuery).resolve();
                transaction.commit();
            }
        }
        // end::delete[]
        // tag::update[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String updateQuery = """
                                    match
                                    $u isa user, has name "Charlie", has email $e;
                                    delete
                                    $u has $e;
                                    insert
                                    $u has email "charles@vaticle.com";
                                    """;
                long response_count = transaction.query().update(updateQuery).count();
                if (response_count == 1) {
                    transaction.commit();
                } else {
                    transaction.close();
                }
            }
        }
        // end::update[]
        // tag::fetch[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.READ)) {
                String fetchQuery = """
                                    match
                                    $u isa user;
                                    fetch
                                    $u: name, email;
                                    """;
                int[] ctr = new int[1];
                transaction.query().fetch(fetchQuery).forEach(result -> System.out.println("Email #" + (++ctr[0]) + ": " + result.toString()));
            }
        }
        // end::fetch[]
        // tag::get[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.READ)) {
                String getQuery = """
                                    match
                                    $u isa user, has email $e;
                                    get
                                    $e;
                                    """;
                int[] ctr = new int[1];
                transaction.query().get(getQuery).forEach(result -> System.out.println("Email #" + (++ctr[0]) + ": " + result.get("e").asAttribute().getValue().toString()));
            }
        }
        // end::get[]
        // tag::infer-rule[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                String defineQuery = """
                                    define
                                    rule users:
                                    when {
                                        $u isa user;
                                    } then {
                                        $u has name "User";
                                    };
                                    """;
                transaction.query().define(defineQuery).resolve();
                transaction.commit();
            }
        }
        // end::infer-rule[]
        // tag::infer-fetch[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            TypeDBOptions options = new TypeDBOptions().infer(true);
            try (TypeDBTransaction transaction = session.transaction(TypeDBTransaction.Type.READ, options)) {
                String fetchQuery = """
                                    match
                                    $u isa user;
                                    fetch
                                    $u: name, email;
                                    """;
                int[] ctr = new int[1];
                transaction.query().fetch(fetchQuery).forEach(result -> System.out.println("Email #" + (++ctr[0]) + ": " + result.toString()));
            }
        }
        // end::infer-fetch[]
        // tag::types-editing[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction Transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                AttributeType tag = Transaction.concepts().putAttributeType("tag", Value.Type.STRING).resolve();
                Transaction.concepts().getRootEntityType().getSubtypes(Transaction, Concept.Transitivity.EXPLICIT).forEach(result -> {
                    System.out.println(result.getLabel().toString());
                    if (! result.isAbstract()) {
                        result.setOwns(Transaction,tag).resolve();
                    }
                });
                Transaction.commit();
            }
        }
        // end::types-editing[]
        // tag::types-api[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction Transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                EntityType user = Transaction.concepts().getEntityType("user").resolve();
                EntityType admin = Transaction.concepts().putEntityType("admin").resolve();
                admin.setSupertype(Transaction, user).resolve();
                EntityType root_entity = Transaction.concepts().getRootEntityType();
                root_entity.getSubtypes(Transaction, Concept.Transitivity.TRANSITIVE).forEach(result -> System.out.println(result.getLabel().name()));
                Transaction.commit();
            }
        }
        // end::types-api[]
        // tag::rules-api[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.SCHEMA)) {
            try (TypeDBTransaction Transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                Rule oldRule = Transaction.logic().getRule("users").resolve();
                System.out.println("Rule label: " + oldRule.getLabel());
                System.out.println("  Condition: " + oldRule.getWhen().toString());
                System.out.println("  Conclusion: " + oldRule.getThen().toString());
                Pattern condition = TypeQL.parsePattern("{$u isa user, has email $e; $e contains '@vaticle.com';}");
                Pattern conclusion = TypeQL.parsePattern("$u has name 'Employee'");
                Rule newRule = Transaction.logic().putRule("Employee", condition, conclusion).resolve();
                Transaction.logic().getRules().forEach(result -> {
                    System.out.println(result.getLabel());
                });
                newRule.delete(Transaction).resolve();
                Transaction.commit();
            }
        }
        // end::rules-api[]
        // tag::data-api[]
        try (TypeDBSession session = driver.session(DB_NAME, TypeDBSession.Type.DATA)) {
            try (TypeDBTransaction Transaction = session.transaction(TypeDBTransaction.Type.WRITE)) {
                Set<ThingType.Annotation> annotations = Collections.emptySet();
                Transaction.concepts().getEntityType("user").resolve().getInstances(Transaction).forEach(user -> {
                            System.out.println("User");
                            user.getHas(Transaction, annotations).forEach(attribute -> {
                                System.out.println(attribute.getType().getLabel().toString()+ ": " + attribute.getValue().toString());
                            });
                });
                Entity new_user = Transaction.concepts().getEntityType("user").resolve().create(Transaction).resolve();
                new_user.delete(Transaction).resolve();
                Transaction.commit();
            }
        }
        // end::data-api[]
        driver.close();
    }
}