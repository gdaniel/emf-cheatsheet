package fr.gdaniel.emf.cheatsheet;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Loads a model and its associated metamodel using EMF reflective API.
 * <p>
 * The goal here is to load an Ecore metamodel without relying on the generated code, register its {@link EPackage}s,
 * then load an instance of this metamodel.
 * <p>
 * The code below prints some information on the loaded (meta)models to illustrate how to use EMF's reflective API to
 * navigate a (meta)model.
 */
public class ReflectiveLoadModel {

    private static Logger log = LoggerFactory.getLogger(ReflectiveLoadModel.class);

    public static void main(String[] args) throws IOException {
        log.info("Loading Graph Metamodel");
        /*
         * Loading models always happens in the context of a ResourceSet.
         */
        ResourceSet rSet = new ResourceSetImpl();
        /*
         * We are loading an .ecore file, so we need to tell EMF what to do with it.
         * In the line below we configure the ResourceSet to provide a particular resource factory to the .ecore
         * extension. Note that Ecore metamodels can always be loaded with XMIResourceFactoryImpl.
         */
        rSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        /*
         * Create an empty resource and give a sensible URI to it.
         * Note that the URI doesn't have to match the file name, it's just a good practice I tend to follow.
         */
        Resource graphMetamodelResource = rSet.createResource(URI.createURI("graph_metamodel.ecore"));
        if (graphMetamodelResource == null) {
            throw new RuntimeException("Cannot create the resource for the graph metamodel");
        }
        /*
         * Load the graph_metamodel.ecore file (in resources/) as an input stream and tell EMF to populate the
         * created resource with its content. After this point the resource will be fully loaded in memory.
         */
        InputStream graphMetamodelInputStream = ReflectiveLoadModel.class.getClassLoader().getResourceAsStream(
                "graph_metamodel.ecore");
        graphMetamodelResource.load(graphMetamodelInputStream, Collections.emptyMap());
        graphMetamodelInputStream.close();
        log.info("Done, see some information on the metamodel below");
        logMetamodelInformation(graphMetamodelResource);
        /*
         * See the documentation of logModelInformation below for more information on this method.
         */
        // logModelInformation(graphMetamodelResource);
        log.info("Loading Graph Model");
        /*
         * We now have a resource that contains our metamodel. Before we load our model we need to register the
         * EPackages of the metamodel we just loaded. Long story short: the ResourceSet needs to know about your
         * EClasses (e.g. Graph) before loading the model (Graph instances).
         * You can comment the line below to see the error thrown by EMF when you do not register the EPackages.
         */
        registerEPackages(rSet, graphMetamodelResource);
        /*
         * Our graph_model.graph file has a specific extension, so we need to register it the same way we registered
         * the .ecore extension. It is still handled by the XMIResourceFactoryImpl.
         */
        rSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("graph", new XMIResourceFactoryImpl());
        /*
         * Load the model the same way we loaded the metamodel. It's exactly the same code as for the metamodel,
         * but under the hood the ResourceSet uses the EPackages we registered to instantiate the content of the
         * model.
         */
        Resource graphModelResource = rSet.createResource(URI.createURI("graph_model.graph"));
        if (graphModelResource == null) {
            throw new RuntimeException("Cannot create the resource for the graph model");
        }
        InputStream graphModelInputStream = ReflectiveLoadModel.class.getClassLoader().getResourceAsStream(
                "graph_model.graph");
        graphModelResource.load(graphModelInputStream, Collections.emptyMap());
        graphModelInputStream.close();
        log.info("Done, see some information on the model below");
        logModelInformation(graphModelResource);
    }

    /**
     * Utility method that prints generic information from a given metamodel.
     * <p>
     * This method logs the list of {@link EClass}es in the metamodel as well as their attributes/references. This
     * method doesn't log anything if the provided {@link Resource} doesn't describe a metamodel (e.g. if it contains
     * a metamodel instance).
     *
     * @param metamodelResource the {@link Resource} containing the metamodel
     */
    private static void logMetamodelInformation(Resource metamodelResource) {
        Iterable<EObject> allContents = metamodelResource::getAllContents;
        for (EObject e : allContents) {
            if (e instanceof EClass eClass) {
                log.info("EClass {}", eClass.getName());
                eClass.getEAllAttributes()
                        .forEach(eAttribute -> log.info("\tAttribute {} (type={})", eAttribute.getName(),
                                eAttribute.getEType().getName()));
                eClass.getEAllReferences()
                        .forEach(eReference -> log.info("\tReference {} (type={})", eReference.getName(),
                                eReference.getEType().getName()));
            }
        }
    }

    /**
     * Utility method that prints generic information from a given model.
     * <p>
     * This method logs the list of instances in the model as well as the <b>value</b> of their attributes/references.
     * This method can be applied to a metamodel, and will consider it as an instance of the Ecore metamodel. This
     * means that instances of Ecore concepts (such as {@link EClass}) will be logged, as well as the values of the
     * attributes/references inherited from Ecore.
     *
     * @param model the {@link Resource} containing the model
     */
    private static void logModelInformation(Resource model) {
        Iterable<EObject> allContents = model::getAllContents;
        for (EObject e : allContents) {
            log.info("Instance of {}", e.eClass().getName());
            e.eClass().getEAllAttributes()
                    .forEach(eAttribute -> log.info("\tAttribute '{}' = {}", eAttribute.getName(), e.eGet(eAttribute)));
            /*
             * Here the logs will probably contain some weird serialization of EMF objects, because we are dealing
             * with references from one object to another.
             */
            e.eClass().getEAllReferences()
                    .forEach(eReference -> log.info("\tReference '{}' = {}", eReference.getName(), e.eGet(eReference)));
        }
    }

    /**
     * Registers the {@link EPackage}s from the provided {@code metamodel} in the given {@code rSet}.
     * <p>
     * This method traverses the provided {@code metamodel} and adds all the {@link EPackage} instances to the
     * {@link ResourceSet#getPackageRegistry()}.
     *
     * @param rSet      the {@link ResourceSet} to update the registry of
     * @param metamodel the metamodel to register the {@link EPackage}s from
     */
    private static void registerEPackages(ResourceSet rSet, Resource metamodel) {
        log.info("Registering EPackages of {}", metamodel.getURI().toString());
        Iterable<EObject> allContents = metamodel::getAllContents;
        for (EObject e : allContents) {
            if (e instanceof EPackage ePackage) {
                rSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
            }
        }
    }
}
