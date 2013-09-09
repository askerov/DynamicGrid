DynamicGrid
===========

Drag and drop GridView for Android provide mechanism to rearrange grid items

Requrements
----------
Drag'n'drop require api 8 (Froyo).
All grid item animations require api 11 (Honeycomb).

Usage
----------
Usage same as for normal GridView expect adapter. Adapter must extends
[AbstractDynamicGridAdapter](https://github.com/askerov/DynamicGrid/blob/master/dynamicgrid/src/org/askerov/dynamicgid/AbstractDynamicGridAdapter.java "AbstractDynamicGridAdapter")
or [BaseDynamicGridAdapter](https://github.com/askerov/DynamicGrid/blob/master/dynamicgrid/src/org/askerov/dynamicgid/BaseDynamicGridAdapter.java "BaseDynamicGridAdapter")   
       
    gridView = (DynamicGridView) findViewById(R.id.dynamic_grid);
    // pass to adapter context, list of items and number of columns count
    gridView.setAdapter(new MyDynamicGridAdapter(this, itemsList, 3));
    
To start Drag'n'drop mode:

    gridView.startEditMode();
    
To stop:

    gridView.stopEditMode();
    
Adding drop listener:

    gridView.setOnDropListener(new DynamicGridView.OnDropListener(){
        @Override
        public void onActionDrop(){
            // stop edit mode immediately after drop item
            gridView.stopEditMode();
            }
        });

You can find more detaile usage examle [here](https://github.com/askerov/DynamicGrid/tree/master/example).  

Credits
--------
DynamicGridView based on [Daniel Olshansky](https://plus.google.com/108153578400873445224/) ListView cell dragging and rearranging [video](https://www.youtube.com/watch?v=_BZIvjMgH-Q).  

