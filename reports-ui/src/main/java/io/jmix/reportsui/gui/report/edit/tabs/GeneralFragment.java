package io.jmix.reportsui.gui.report.edit.tabs;

import io.jmix.core.EntityStates;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.reports.entity.*;
import io.jmix.reportsui.gui.definition.edit.BandDefinitionEditor;
import io.jmix.security.constraint.PolicyStore;
import io.jmix.security.constraint.SecureOperations;
import io.jmix.ui.Notifications;
import io.jmix.ui.RemoveOperation;
import io.jmix.ui.action.Action;
import io.jmix.ui.component.*;
import io.jmix.ui.model.CollectionContainer;
import io.jmix.ui.model.CollectionPropertyContainer;
import io.jmix.ui.model.InstanceContainer;
import io.jmix.ui.screen.*;
import org.bouncycastle.asn1.cms.MetaData;
import org.springframework.beans.factory.annotation.Autowired;

import javax.inject.Named;
import java.util.*;

@UiController("report_ReportEditGeneral.fragment")
@UiDescriptor("general.xml")
public class GeneralFragment extends ScreenFragment {

    @Named("serviceTree")
    protected Tree<BandDefinition> bandTree;

    @Autowired
    protected InstanceContainer<Report> reportDc;

    @Autowired
    protected CollectionContainer<BandDefinition> bandsDc;

    @Autowired
    private CollectionContainer<BandDefinition> availableParentBandsDc;

    @Autowired
    protected Metadata metadata;

    @Autowired
    protected SecureOperations secureOperations;

    @Autowired
    protected PolicyStore policyStore;

    @Autowired
    protected CollectionPropertyContainer<DataSet> dataSetsDc;

    @Autowired
    protected EntityStates entityStates;

    @Autowired
    protected Notifications notifications;

    @Autowired
    private FileUploadField invisibleFileUpload;

    @Autowired
    protected Messages messages;

    @Autowired
    private BandDefinitionEditor bandEditor;

    @Subscribe
    public void onInit(InitEvent event) {
        invisibleFileUpload.addFileUploadSucceedListener(invisibleUpload -> {
            final ReportTemplate defaultTemplate = reportDc.getItem().getDefaultTemplate();
            if (defaultTemplate != null) {
                if (!isTemplateWithoutFile(defaultTemplate)) {
                    //todo
//                    File file = fileUpload.getFile(invisibleFileUpload.getFileName());
//                    try {
//                        byte[] data = FileUtils.readFileToByteArray(file);
//                        defaultTemplate.setContent(data);
//                        defaultTemplate.setName(invisibleFileUpload.getFileName());
//                        templatesDc.modifyItem(defaultTemplate);
//                    } catch (IOException e) {
//                        throw new RuntimeException(String.format(
//                                "An error occurred while uploading file for template [%s]",
//                                defaultTemplate.getCode()));
//                    }
                } else {
                    notifications.create(Notifications.NotificationType.HUMANIZED)
                            .withCaption(messages.getMessage(getClass(), "notification.fileIsNotAllowedForSpecificTypes"))
                            .show();
                }
            } else {
                notifications.create(Notifications.NotificationType.HUMANIZED)
                        .withCaption(messages.getMessage(getClass(), "notification.defaultTemplateIsEmpty"))
                        .show();
            }
        });

        bandsDc.addItemChangeListener(e -> {
            //bandEditor.setBandDefinition(e.getItem());
            bandEditor.setEnabled(e.getItem() != null);
            availableParentBandsDc.getMutableItems().clear();
            if (e.getItem() != null) {
                for (BandDefinition bandDefinition : bandsDc.getItems()) {
                    if (!isChildOrEqual(e.getItem(), bandDefinition) ||
                            Objects.equals(e.getItem().getParentBandDefinition(), bandDefinition)) {
                        availableParentBandsDc.getMutableItems().add(bandDefinition);
                    }
                }
            }
        });

        bandEditor.getBandDefinitionDs().addItemPropertyChangeListener(e -> {
            if ("parentBandDefinition".equals(e.getProperty())) {
                BandDefinition previousParent = (BandDefinition) e.getPrevValue();
                BandDefinition parent = (BandDefinition) e.getValue();

                if (e.getValue() == e.getItem()) {
                    e.getItem().setParentBandDefinition(previousParent);
                } else {
                    //todo
                    //treeDc.refresh();
                    previousParent.getChildrenBandDefinitions().remove(e.getItem());
                    parent.getChildrenBandDefinitions().add(e.getItem());
                }

                if (e.getPrevValue() != null) {
                    orderBandDefinitions(previousParent);
                }

                if (e.getValue() != null) {
                    orderBandDefinitions(parent);
                }
            }
            //todo
            //treeDc.modifyItem(e.getItem());
        });
    }



    @Subscribe("serviceTree.create")
    public void onServiceTreeCreate(Action.ActionPerformedEvent event) {
        BandDefinition parentDefinition = bandsDc.getItem();
        Report report = reportDc.getItem();
        // Use root band as parent if no items selected
        if (parentDefinition == null) {
            parentDefinition = report.getRootBandDefinition();
        }
        if (parentDefinition.getChildrenBandDefinitions() == null) {
            parentDefinition.setChildrenBandDefinitions(new ArrayList<>());
        }


        orderBandDefinitions(parentDefinition);

        BandDefinition newBandDefinition = metadata.create(BandDefinition.class);
        newBandDefinition.setName("newBand" + (parentDefinition.getChildrenBandDefinitions().size() + 1));
        newBandDefinition.setOrientation(Orientation.HORIZONTAL);
        newBandDefinition.setParentBandDefinition(parentDefinition);
        if (parentDefinition.getChildrenBandDefinitions() != null) {
            newBandDefinition.setPosition(parentDefinition.getChildrenBandDefinitions().size());
        } else {
            newBandDefinition.setPosition(0);
        }
        newBandDefinition.setReport(report);
        parentDefinition.getChildrenBandDefinitions().add(newBandDefinition);

        bandsDc.getMutableItems().add(newBandDefinition);

        bandTree.expandTree();
        bandTree.setSelected(newBandDefinition);//let's try and see if it increases usability

        bandTree.focus();
    }

    @Install(to = "serviceTree.create", subject = "enabledRule")
    private boolean serviceTreeCreateEnabledRule() {
        return secureOperations.isEntityUpdatePermitted(metadata.getClass(Report.class), policyStore);
    }

    @Subscribe("serviceTree.remove")
    public void onServiceTreeRemove(Action.ActionPerformedEvent event) {
        Set<BandDefinition> selected = bandTree.getSelected();
        if (selected != null) {
            removeChildrenCascade(selected);
            for (Object object : selected) {
                BandDefinition definition = (BandDefinition) object;
                if (definition.getParentBandDefinition() != null) {
                    orderBandDefinitions(((BandDefinition) object).getParentBandDefinition());
                }
            }
        }
        bandTree.focus();
    }

    @Install(to = "serviceTree.remove", subject = "enabledRule")
    private boolean serviceTreeRemoveEnabledRule() {
            Object selectedItem = bandTree.getSingleSelected();
            if (selectedItem != null) {
                return !Objects.equals(reportDc.getItem().getRootBandDefinition(), selectedItem);
            }

        return false;
    }


    private void removeChildrenCascade(Collection selected) {
        for (Object o : selected) {
            BandDefinition definition = (BandDefinition) o;
            BandDefinition parentDefinition = definition.getParentBandDefinition();
            if (parentDefinition != null) {
                definition.getParentBandDefinition().getChildrenBandDefinitions().remove(definition);
            }

            if (definition.getChildrenBandDefinitions() != null) {
                removeChildrenCascade(new ArrayList<>(definition.getChildrenBandDefinitions()));
            }

            if (definition.getDataSets() != null) {
                bandsDc.setItem(definition);
                for (DataSet dataSet : new ArrayList<>(definition.getDataSets())) {
                    if (entityStates.isNew(dataSet)) {
                        dataSetsDc.getMutableItems().remove(dataSet);
                    }
                }
            }
            bandsDc.getMutableItems().remove(definition);
        }
    }

    protected void orderBandDefinitions(BandDefinition parent) {
        if (parent.getChildrenBandDefinitions() != null) {
            List<BandDefinition> childrenBandDefinitions = parent.getChildrenBandDefinitions();
            for (int i = 0, childrenBandDefinitionsSize = childrenBandDefinitions.size(); i < childrenBandDefinitionsSize; i++) {
                BandDefinition bandDefinition = childrenBandDefinitions.get(i);
                bandDefinition.setPosition(i);
            }
        }
    }

    @Subscribe("up")
    public void onUpClick(Button.ClickEvent event) {
        BandDefinition definition = bandTree.getSingleSelected();
        if (definition != null && definition.getParentBandDefinition() != null) {
            BandDefinition parentDefinition = definition.getParentBandDefinition();
            List<BandDefinition> definitionsList = parentDefinition.getChildrenBandDefinitions();
            int index = definitionsList.indexOf(definition);
            if (index > 0) {
                BandDefinition previousDefinition = definitionsList.get(index - 1);
                definition.setPosition(definition.getPosition() - 1);
                previousDefinition.setPosition(previousDefinition.getPosition() + 1);

                definitionsList.set(index, previousDefinition);
                definitionsList.set(index - 1, definition);

                bandTree.repaint();
            }
        }
    }



//    @Override
//    protected boolean isApplicable() {
//        if (target != null) {
//            BandDefinition selectedItem = (BandDefinition) target.getSingleSelected();
//            return selectedItem != null && selectedItem.getPosition() > 0 && isUpdatePermitted();
//        }
//
//        return false;
//    }

    @Subscribe("down")
    public void onDownClick(Button.ClickEvent event) {
        BandDefinition definition = bandTree.getSingleSelected();
        if (definition != null && definition.getParentBandDefinition() != null) {
            BandDefinition parentDefinition = definition.getParentBandDefinition();
            List<BandDefinition> definitionsList = parentDefinition.getChildrenBandDefinitions();
            int index = definitionsList.indexOf(definition);
            if (index < definitionsList.size() - 1) {
                BandDefinition nextDefinition = definitionsList.get(index + 1);
                definition.setPosition(definition.getPosition() + 1);
                nextDefinition.setPosition(nextDefinition.getPosition() - 1);

                definitionsList.set(index, nextDefinition);
                definitionsList.set(index + 1, definition);

                bandTree.repaint();
            }
        }
    }


//        @Override
//        protected boolean isApplicable() {
//            if (target != null) {
//                BandDefinition bandDefinition = (BandDefinition) target.getSingleSelected();
//                if (bandDefinition != null) {
//                    BandDefinition parent = bandDefinition.getParentBandDefinition();
//                    return parent != null &&
//                            parent.getChildrenBandDefinitions() != null &&
//                            bandDefinition.getPosition() < parent.getChildrenBandDefinitions().size() - 1
//                            && isUpdatePermitted();
//                }
//            }
//            return false;
//        }

    protected boolean isTemplateWithoutFile(ReportTemplate template) {
        return template.getOutputType() == JmixReportOutputType.chart ||
                template.getOutputType() == JmixReportOutputType.table ||
                template.getOutputType() == JmixReportOutputType.pivot;


    }

    protected boolean isChildOrEqual(BandDefinition definition, BandDefinition child) {
        if (definition.equals(child)) {
            return true;
        } else if (child != null) {
            return isChildOrEqual(definition, child.getParentBandDefinition());
        } else {
            return false;
        }
    }
}
