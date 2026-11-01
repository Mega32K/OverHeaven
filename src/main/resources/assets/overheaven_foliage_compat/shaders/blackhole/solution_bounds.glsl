uniform int SolutionBounded;
uniform ivec4 SolutionBounds;
bool solutionContains(ivec2 p) {
#ifdef UNBOUNDED_TARGET
    return true;
#else
    return SolutionBounded==0 || (all(greaterThanEqual(p,SolutionBounds.xy)) && all(lessThan(p,SolutionBounds.zw)));
#endif
}
