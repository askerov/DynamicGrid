DynamicGrid
===========

Drag and drop GridView for Android.

Depricated
===========
It's much better to use solutions based on recycler view. For example https://github.com/h6ah4i/android-advancedrecyclerview

Demo
----

<a href="http://www.youtube.com/watch?feature=player_embedded&v=zlzNvxksIfY
" target="_blank"><img src="http://img.youtube.com/vi/zlzNvxksIfY/0.jpg" 
alt="example" width="240" height="180" border="10" /></a>

Requirements
----------
Rearranging items require api 8 (Froyo).
All grid item animations require api 11 (Honeycomb).

Usage
----------
All the same as for normal GridView. Adapter must extends
[AbstractDynamicGridAdapter](https://github.com/askerov/DynamicGrid/blob/master/dynamicgrid/src/org/askerov/dynamicgid/AbstractDynamicGridAdapter.java "AbstractDynamicGridAdapter")
or [BaseDynamicGridAdapter](https://github.com/askerov/DynamicGrid/blob/master/dynamicgrid/src/org/askerov/dynamicgid/BaseDynamicGridAdapter.java "BaseDynamicGridAdapter")   
       
```java
gridView = (DynamicGridView) findViewById(R.id.dynamic_grid);
// pass to adapter context, list of items and number of columns count
gridView.setAdapter(new MyDynamicGridAdapter(this, itemsList, 3));
```
    
To start Drag'n'drop mode:

```java
gridView.startEditMode();
```
    
Or from onItemClik() and onItemLongClick()

```java
gridView.startEditMode(position);
```
    
To stop:

```java
gridView.stopEditMode();
```
    
Adding drop listener:

```java
gridView.setOnDropListener(new DynamicGridView.OnDropListener(){
    @Override
    public void onActionDrop(){
        // stop edit mode immediately after drop item
        gridView.stopEditMode();
        }
    });
```

You can find more detailed usage example [here](https://github.com/askerov/DynamicGrid/tree/master/example).  

Credits
--------
DynamicGridView based on [Daniel Olshansky](https://plus.google.com/108153578400873445224/) ListView cell dragging and rearranging [example](https://www.youtube.com/watch?v=_BZIvjMgH-Q).  

