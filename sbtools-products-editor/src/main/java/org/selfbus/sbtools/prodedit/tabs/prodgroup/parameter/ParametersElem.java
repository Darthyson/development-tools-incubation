package org.selfbus.sbtools.prodedit.tabs.prodgroup.parameter;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Box;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import org.selfbus.sbtools.common.gui.actions.BasicAction;
import org.selfbus.sbtools.common.gui.components.CloseableComponent;
import org.selfbus.sbtools.common.gui.misc.ImageCache;
import org.selfbus.sbtools.common.gui.tree.MutableIconTreeNode;
import org.selfbus.sbtools.prodedit.actions.CollapseAllTreeAction;
import org.selfbus.sbtools.prodedit.actions.ExpandAllTreeAction;
import org.selfbus.sbtools.prodedit.actions.RemoveSelectionInTreeAction;
import org.selfbus.sbtools.prodedit.binding.SelectionInTree;
import org.selfbus.sbtools.prodedit.internal.I18n;
import org.selfbus.sbtools.prodedit.model.prodgroup.ProductGroup;
import org.selfbus.sbtools.prodedit.model.prodgroup.VirtualDevice;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.AbstractParameterContainer;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.AbstractParameterNode;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.CommunicationObject;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.Parameter;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.ParameterCategory;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.ParameterTreeModel;
import org.selfbus.sbtools.prodedit.model.prodgroup.parameter.ParameterType;
import org.selfbus.sbtools.prodedit.model.prodgroup.program.ApplicationProgram;
import org.selfbus.sbtools.prodedit.renderer.ParameterTreeCellRenderer;
import org.selfbus.sbtools.prodedit.tabs.internal.AbstractCategoryElem;

/**
 * An element that displays the {@link ParameterType parameter types} of a {@link VirtualDevice
 * device}.
 */
public class ParametersElem extends AbstractCategoryElem implements CloseableComponent
{
   // private final Logger LOGGER = LoggerFactory.getLogger(ParametersElem.class);

   private final ProductGroup group;
   private ApplicationProgram program;

   private final SelectionInTree selectionInTree = new SelectionInTree(new DefaultTreeModel(
      new MutableIconTreeNode("/")));
   // private final PresentationModel<AbstractParameterNode> detailsModel = new
   // PresentationModel<AbstractParameterNode>(selectionInTree);

   private final JTree paramTree = new JTree(selectionInTree);
   private final ParameterTreeCellRenderer paramTreeCellRenderer = new ParameterTreeCellRenderer(
      paramTree.getCellRenderer());

   private final ParameterPanel paramPanel = new ParameterPanel(this);
   private final CommunicationObjectPanel comObjectPanel = new CommunicationObjectPanel(this);
   private final JPanel emptyPanel = new JPanel();
   private final JScrollPane detailsScrollPane;
   private JPanel currentPanel;

   /**
    * Create a {@link Product products} display element.
    * 
    * @param group
    *           - the products group to display.
    */
   public ParametersElem(ProductGroup group)
   {
      this.group = group;

      toolBar = new JToolBar();
      listScrollPane = new JScrollPane(paramTree);

      detailsPanel = new JPanel(new BorderLayout(0, 0));
      detailsScrollPane = new JScrollPane(detailsPanel);

      selectionInTree.bindTo(paramTree);
      paramTree.setCellRenderer(paramTreeCellRenderer);
      paramTree.setDragEnabled(true);
      paramTree.setDropMode(DropMode.ON_OR_INSERT);
      paramTree.setTransferHandler(new ParameterTransferHandler(selectionInTree));

      setupToolBar();

      currentPanel = emptyPanel;
      detailsPanel.add(currentPanel, BorderLayout.CENTER);

      selectionInTree.addPropertyChangeListener(SelectionInTree.PROPERTY_SELECTION, new PropertyChangeListener()
      {
         @Override
         public void propertyChange(PropertyChangeEvent e)
         {
            Object newValue = e.getNewValue();
            JPanel newCurrentPanel = null;

            if (newValue instanceof Parameter)
            {
               newCurrentPanel = paramPanel;
               paramPanel.setParameter((Parameter) newValue);
            }
            else if (newValue instanceof CommunicationObject)
            {
               newCurrentPanel = comObjectPanel;
               comObjectPanel.setCommunicationObject((CommunicationObject) newValue);
            }
            else
            {
               newCurrentPanel = emptyPanel;
            }

            showDetailsPanel(newCurrentPanel);
         }
      });
   }

   /**
    * Setup the tool bar.
    */
   private void setupToolBar()
   {
      // Action: add a com-object
      toolBar.add(new BasicAction("addComObj", I18n.getMessage("ParametersElem.addComObjectTip"), ImageCache
         .getIcon("icons/connect_new"))
      {
         private static final long serialVersionUID = 1;

         @Override
         public void actionEvent(ActionEvent event)
         {
            Object selectedObject = selectionInTree.getSelection();

            if (selectedObject instanceof CommunicationObject)
               selectedObject = ((CommunicationObject) selectedObject).getParent();

            if (selectedObject == null)
               selectedObject = program.getParameterRoot();

            CommunicationObject comObject = program
               .createCommunicationObject((AbstractParameterContainer) selectedObject);
            paramTree.updateUI();
            selectionInTree.setSelection(comObject);
            paramTree.scrollRowToVisible(paramTree.getSelectionRows()[0]);
         }
      });

      // Action: add a page
      toolBar.add(new BasicAction("addPage", I18n.getMessage("ParametersElem.addPageTip"), ImageCache
         .getIcon("icons/page_new"))
      {
         private static final long serialVersionUID = 1;

         @Override
         public void actionEvent(ActionEvent event)
         {
            createParameter(ParameterCategory.PAGE, program.getEmptyParameterType());
         }
      });

      // Action: add a parameter
      toolBar.add(new BasicAction("addParam", I18n.getMessage("ParametersElem.addParamTip"), ImageCache
         .getIcon("icons/parameter_new"))
      {
         private static final long serialVersionUID = 1;

         @Override
         public void actionEvent(ActionEvent event)
         {
            createParameter(ParameterCategory.VALUE, program.getEmptyParameterType());
         }
      });

      toolBar.addSeparator();

      // Action: remove the current virtual device
      toolBar.add(new RemoveSelectionInTreeAction(selectionInTree, I18n.getMessage("ParametersElem.removeTip")));

      toolBar.add(Box.createHorizontalGlue());

      // Action: expand the list
      toolBar.add(new ExpandAllTreeAction(paramTree));

      // Action: collapse the list
      toolBar.add(new CollapseAllTreeAction(paramTree));
   }

   /**
    * Create a communication object that is a child of the currently selected parameter. If a
    * communication object is selected, it's parent will be used.
    */
   public void createCommunicationObject()
   {
      AbstractParameterContainer parent = getParentForNewNode();
      CommunicationObject comObject = program.createCommunicationObject(parent);
      paramTree.updateUI();
      setSelected(comObject);
   }

   /**
    * Create a parameter that is a child of the currently selected parameter. If a communication
    * object is selected, it's parent will be used.
    * 
    * @param category - the category of the parameter
    * @param type - the type of the parameter
    */
   public void createParameter(ParameterCategory category, ParameterType type)
   {
      AbstractParameterContainer parent = getParentForNewNode();
      Parameter param = program.createParameter(type, parent);
      paramTree.updateUI();
      setSelected(param);
   }

   /**
    * @return The parent parameter node for a new parameter or communication object.
    */
   private AbstractParameterContainer getParentForNewNode()
   {
      Object selectedObject = selectionInTree.getSelection();

      if (selectedObject instanceof CommunicationObject)
         return (AbstractParameterContainer) ((CommunicationObject) selectedObject).getParent();

      if (selectedObject == null)
         return (AbstractParameterContainer) program.getParameterRoot();

      return (AbstractParameterContainer) selectedObject;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close()
   {
      paramPanel.close();
      selectionInTree.release();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getName()
   {
      return I18n.getMessage("ParametersElem.title");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JComponent getDetailsPanel()
   {
      return detailsScrollPane;
   }

   /**
    * Set the virtual device.
    * 
    * @param device
    *           - the device to set
    */
   public void setDevice(VirtualDevice device)
   {
      program = group.getProgram(device);

      ParameterTreeModel paramTreeModel;
      if (program != null)
         paramTreeModel = program.getParameterTreeModel();
      else paramTreeModel = new ParameterTreeModel();

      paramPanel.setProgram(program);
      paramTreeCellRenderer.setProgram(program);
      if (paramTreeModel != null)
      {
         selectionInTree.setTree(paramTreeModel);
         paramTree.setModel(paramTreeModel);
      }
   }

   /**
    * Set the selected parameter.
    * 
    * @param paramId
    *           - the ID of the parameter.
    */
   public void setSelectedParam(int paramId)
   {
      ParameterTreeModel paramTreeModel = (ParameterTreeModel) paramTree.getModel();
      selectionInTree.setSelection(paramTreeModel.findById(paramId));

      int rows[] = paramTree.getSelectionRows();
      if (rows != null && rows.length > 0)
         paramTree.scrollRowToVisible(rows[0]);
      else showDetailsPanel(emptyPanel);
   }

   /**
    * Set the selected parameter node.
    * 
    * @param node
    *           - the parameter node to make visible.
    */
   public void setSelected(AbstractParameterNode node)
   {
      setSelectedParam(node.getId());
   }

   /**
    * Set the details panel that is currently visible.
    * 
    * @param panel
    *           - the details panel to show.
    */
   protected void showDetailsPanel(JPanel panel)
   {
      if (panel != currentPanel)
      {
         detailsPanel.remove(currentPanel);
         currentPanel.setVisible(false);

         currentPanel = panel;
         detailsPanel.add(currentPanel, BorderLayout.CENTER);
         currentPanel.setVisible(true);
      }
   }
}
