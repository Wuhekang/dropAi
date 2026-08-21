from dataclasses import dataclass
FIT_MARGIN=24;MIN_SCALE=.10;MAX_FIT_SCALE=1.50
@dataclass
class DiagramBounds:left:float;top:float;right:float;bottom:float
@dataclass
class ViewTransform:scale:float;offset_x:float;offset_y:float
def fit_transform(bounds,viewport_width,viewport_height):
 w=max(1,bounds.right-bounds.left);h=max(1,bounds.bottom-bounds.top);aw=max(1,viewport_width-2*FIT_MARGIN);ah=max(1,viewport_height-2*FIT_MARGIN);scale=max(MIN_SCALE,min(aw/w,ah/h,MAX_FIT_SCALE));return ViewTransform(scale,(viewport_width-w*scale)/2-bounds.left*scale,(viewport_height-h*scale)/2-bounds.top*scale)
