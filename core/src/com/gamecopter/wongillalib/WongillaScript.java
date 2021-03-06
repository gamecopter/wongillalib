package com.gamecopter.wongillalib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.XmlReader;
import com.gamecopter.wongillalib.factories.WongillaDefaultAttributeFactory;
import com.gamecopter.wongillalib.factories.WongillaDefaultElementFactory;
import com.gamecopter.wongillalib.interfaces.LoadLibraryEventListener;
import com.gamecopter.wongillalib.interfaces.SceneEventListener;
import com.gamecopter.wongillalib.services.AssetService;
import com.gamecopter.wongillalib.services.ScopeService;
import com.gamecopter.wongillalib.scripts.AttributeDirective;
import com.gamecopter.wongillalib.scripts.ElementDirective;
import com.gamecopter.wongillalib.scripts.Namespace;
import com.gamecopter.wongillalib.utils.ScriptInterpreter;


import java.util.ArrayList;

/**
 * Created by Kevin Wong on 6/23/2014.
 */
public class WongillaScript implements Disposable {

    XmlReader.Element xml_element;

    Namespace rootNamespace = Namespace.createRootNamespace("ROOT");

    ArrayList<LoadLibraryEventListener> loadLibraryEventListeners = new ArrayList<LoadLibraryEventListener>();
    ArrayList<String> importedNamespaceNames = new ArrayList<String>();

    AssetService assetService = new AssetService();
    private ScopeService scopeService;
    Stage stage;
    ScriptInterpreter interpreter;

    public ScriptInterpreter getScriptInterpreter()
    {
        return this.interpreter;
    }

    public void setScriptInterpreter(ScriptInterpreter interpreter)
    {
        this.interpreter = interpreter;
    }

    public WongillaScript(Stage s, Object rootController) {
        this(s, rootController, true);
    }

    public WongillaScript(Stage s, Object rootController, boolean loadDefaultLibraries) {
        stage = s;
        scopeService = new ScopeService(this, rootController);

        if(loadDefaultLibraries)
            this.loadDefaultLibraries();
    }

    public void loadDefaultLibraries()
    {
        LoadLibraryEventListener listener = createDefaultNamespaceEventListener(scopeService, assetService);
        loadLibraryEventListeners.add(listener);

        // no need to use import tag on wongilla script to import this default namespace
        importedNamespaceNames.add(listener.getNamespaceFullName());
    }

    public void loadLibraries()
    {

        for(LoadLibraryEventListener listener : loadLibraryEventListeners)
        {
            // only import if it is never created before.
            if(!Namespace.isNamespaceCreated(rootNamespace, "ROOT." + listener.getNamespaceFullName())) {
                listener.addLibrary(rootNamespace, scopeService, assetService);
            }
        }

    }

    protected LoadLibraryEventListener createDefaultNamespaceEventListener(ScopeService scopeService, AssetService assetService)
    {
        LoadLibraryEventListener defaultListener = new LoadLibraryEventListener() {
            @Override
            public String getNamespaceFullName() {
                return "wongila.libgdx.scene2d";
            }

            @Override
            public void addLibrary(Namespace rootNamespace, ScopeService scopeService, AssetService assetService) {
                String namespaceFullName = getNamespaceFullName();

                    WongillaDefaultElementFactory elementFactory = new WongillaDefaultElementFactory(scopeService, assetService);
                    WongillaDefaultAttributeFactory attributeFactory = new WongillaDefaultAttributeFactory(scopeService);

                    ArrayList<ElementDirective> DirectiveElements = new ArrayList<ElementDirective>();
                    ArrayList<AttributeDirective> CommonAttributes = new ArrayList<AttributeDirective>();

                    DirectiveElements.addAll(elementFactory.createDirectives());
                    CommonAttributes.addAll(attributeFactory.createDirectives());

                    rootNamespace.namespace(namespaceFullName).addElements(DirectiveElements);
                    rootNamespace.namespace(namespaceFullName).addAttributes(CommonAttributes);
            }
        };

        return defaultListener;
    }


    public void addController(String name, Object controller) {
        scopeService.addController(name, controller);
    }

    public Object getController(String name) {
        return scopeService.getController(name);
    }

    public Actor CreateActorFromElement(XmlReader.Element e) {
        String name = e.getName();
        Actor a = null;


        for(String namespaceName : importedNamespaceNames) {
            Namespace importedNamespace = rootNamespace.namespace(namespaceName);

            if(!importedNamespace.isElementListEmpty()) {
                ArrayList<ElementDirective> DirectiveElements = importedNamespace.getElements();

                for (ElementDirective d : DirectiveElements) {

                    if (name.equalsIgnoreCase(d.getName())) {
                        a = d.processDirective(null, e);

                        if (a != null) {

                            if (d.isApplyCommonAttribute()) {
                                if (!importedNamespace.isAttributeListEmpty()) {

                                    ArrayList<AttributeDirective> CommonAttributes = importedNamespace.getAttributes();
                                    for (AttributeDirective ad : CommonAttributes) {
                                        a = ad.processDirective(a, e);
                                    }

                                }
                            }

                        }

                        return a;
                    }
                }
            }
        }

        return a;
    }

    public UIScene getCurrentScene() {
        Array<Actor> actors = stage.getActors();

        for (Actor a : actors) {

            // only one scene per stage available
            if (a instanceof UIScene) {
                return (UIScene) a;
            }
        }

        return null;
    }


    protected UIScene lastRenderedScene = null;

    // this loads everything into stage.
    public void RenderScene(String startSceneName) {
        Array<XmlReader.Element> scenes;

        if (startSceneName.endsWith(".xml")) {
            try {
                startSceneName = LoadFile(startSceneName);
            } catch (Exception io) {

            }
        }

        scenes = xml_element.getChildrenByName("scene");

        int Count = 0;

        for (XmlReader.Element sceneElement : scenes) {
            String sceneName = sceneElement.getAttribute("name", "Scene_" + Count++);

            if (startSceneName.equalsIgnoreCase(sceneName)) {
                stage.clear();
                // reset controller
                scopeService.setCurrentController(null);
                scopeService.clearScopeVariables(); // clear all temporary variables


                String controllerName = sceneElement.getAttribute("controller", null);
                scopeService.setCurrentController(controllerName);

                int count = sceneElement.getChildCount();
                UIScene currentScene = new UIScene();
                currentScene.setName(sceneName);
                currentScene.setControllerName(controllerName);
                currentScene.setFillParent(true); // fixed: the table is not full-sized based on window size

                for (int i = 0; i < count; i++) {
                    XmlReader.Element child = sceneElement.getChild(i);

                    // use factory and dictionary
                    // todo:
                    // Factory factory = Dictionary.get(ElementName);
                    // Actor a = Factory.CreateActor();
                    // gScene.addActor(a);

                    Actor a = CreateActorFromElement(child);

                    if (a != null) {
                        currentScene.addActor(a);
                    }

                }
                Object controller = scopeService.getCurrentController();

                if(lastRenderedScene!=null)
                {
                    // ToDO: perform exitScene here
                }

                if (controller instanceof SceneEventListener) {
                    ((SceneEventListener) controller).enterScene(currentScene, this);
                }

                stage.addActor(currentScene);
                lastRenderedScene = currentScene;
                break;
            }

        }


    }


    public String LoadFile(String filename) {
        FileHandle xmlFile = Gdx.files.internal("scenes/" + filename);

        String script = xmlFile.readString();
        XmlReader xml = new XmlReader();
        xml_element = xml.parse(script);

        String startSceneName = xml_element.getAttribute("startScene", null);

        if (startSceneName == null) {
            Array<XmlReader.Element> scenes = xml_element.getChildrenByName("scene");

            startSceneName = scenes.get(0).get("name");

        }

        return startSceneName;
    }

    public void LoadStage() {
        FileHandle xmlFile = Gdx.files.internal("scenes/index.xml");

        String script = xmlFile.readString();
        XmlReader xml = new XmlReader();
        xml_element = xml.parse(script);

        String startSceneName = xml_element.getAttribute("startScene", null);
        String skinPath = xml_element.getAttribute("skin", null);

        assetService.setCurrentSkinPath(skinPath);

        assetService.loadAssets();

        if (startSceneName == null) {
            Array<XmlReader.Element> scenes = xml_element.getChildrenByName("scene");

            startSceneName = scenes.get(0).get("name");

        }

        RenderScene(startSceneName);
    }


    public void draw() {
        stage.draw();
    }

    public Stage getStage() {
        return this.stage;
    }

    public void update() {

        stage.act(Gdx.graphics.getDeltaTime());

        UIScene scene = this.getCurrentScene();

        if (scene != null ) {
            Object controller = scopeService.getController(scene.getControllerName());

            if(controller instanceof  SceneEventListener) {
                SceneEventListener listener = (SceneEventListener) controller;
                if (listener != null)
                    listener.updateScene(scene, this, scopeService, assetService);
            }

        }

    }


    protected Actor findTable(Group g) {
        Array<Actor> actors = g.getChildren();

        for (Actor a : actors) {
            if (a instanceof Table && !(a instanceof Button)) {
                return a;
            }
        }

        return null;
    }

    public void drawDebug() {
        UIScene scene = this.getCurrentScene();

        if (scene != null) {
            Actor table1 = findTable(scene);

            if (table1 != null) {
                Table table = (Table) table1;

            //    if (table.getDebug() != Table.Debug.none)
                 //   table.drawDebug(stage);
            }
        }
    }


    public ScopeService getScopeService() {
        return scopeService;
    }

    @Override
    public void dispose() {

        assetService.dispose();
    }

    /*
     public Actor CreateTree(XmlReader.Element e) {
        try {
            Tree tree = new Tree(this.defaultSkin);

            LoadCommonAttributesForActor(e, tree);
            final Tree.Node moo1 = new Tree.Node(new TextButton("moo1", this.defaultSkin));
            final Tree.Node moo2 = new Tree.Node(new TextButton("moo2", this.defaultSkin));
            final Tree.Node moo3 = new Tree.Node(new TextButton("moo3", this.defaultSkin));
            final Tree.Node moo4 = new Tree.Node(new TextButton("moo4", this.defaultSkin));
            final Tree.Node moo5 = new Tree.Node(new TextButton("moo5", this.defaultSkin));
            tree.add(moo1);
            tree.add(moo2);
            tree.add(moo3);
            tree.add(moo4);
            tree.add(moo5);

            return tree;
        } catch (Exception ex) {
            return CreateErrorLabel(ex);
        }

    }
     */
}
