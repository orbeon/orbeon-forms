package org.orbeon.oxf.properties

import cats.data.NonEmptyList
import org.orbeon.dom.QName
import org.orbeon.oxf.properties.PropertySet.PropertyNodeT
import org.orbeon.properties.api.ETag


class CombinedMap[K, V](
  private val map1: collection.Map[K, V],
  private val map2: collection.Map[K, V]
) extends collection.Map[K, V] {

  override def get(key: K): Option[V] = map2.get(key).orElse(map1.get(key))

  override def iterator: Iterator[(K, V)] =
    map1.view.filterKeys(! map2.contains(_)).iterator ++ map2.iterator

  override def -(key: K): collection.Map[K, V] =
    throw new UnsupportedOperationException("`CombinedMap.-` is not supported")

  override def -(key1: K, key2: K, keys: K*): collection.Map[K, V] =
    throw new UnsupportedOperationException("`CombinedMap.-` is not supported")

  override def size: Int =
    map2.size + map1.keys.count(! map2.contains(_))

  override def contains(key: K): Boolean =
    map2.contains(key) || map1.contains(key)

  override def keySet: collection.Set[K] =
    map1.keySet.union(map2.keySet)
}

class CombinedPropertyNode(n1: PropertyNodeT, n2: PropertyNodeT) extends PropertyNodeT {

  def property: Option[Property] =
    n2.property.orElse(n1.property)

  def hasChildren: Boolean =
    n2.hasChildren || n1.hasChildren

  def get(k: String): Option[PropertyNodeT] =
    (n1.get(k), n2.get(k)) match {
      case (Some(c1), Some(c2)) => Some(new CombinedPropertyNode(c1, c2))
      case (Some(c1), None)     => Some(c1)
      case (None, Some(c2))     => Some(c2)
      case (None, None)         => None
    }

  // xxx FIXME
  def iterable: Iterable[(String, PropertyNodeT)] = {
    val keys = n1.iterable.map(_._1).toSet.union(n2.iterable.map(_._1).toSet)
    keys.map { key =>
      key -> get(key).get
    }
  }
}

class CombinedPropertySet(ps1: PropertySet, ps2: PropertySet) extends PropertySet {

  protected[orbeon] val propertiesByName: collection.Map[String, Property] =
    new CombinedMap(ps1.propertiesByName, ps2.propertiesByName)

  protected[orbeon] val propertiesTree: PropertySet.PropertyNodeT =
    new CombinedPropertyNode(ps1.propertiesTree, ps2.propertiesTree)

  // Used for https://github.com/orbeon/orbeon-forms/issues/6980, needs to increase when properties change
  val eTag: ETag = s"${ps1.eTag}:${ps2.eTag}"
}

class CombinedPropertyStore(ps1: PropertyStore, ps2: PropertyStore) extends PropertyStore {

  def globalPropertySet: PropertySet =
    new CombinedPropertySet(ps1.globalPropertySet, ps2.globalPropertySet)

  // Cache of processor property sets
  @volatile
  private var processorPropertySets = Map.empty[QName, PropertySet]

  def processorPropertySet(processorQName: QName): PropertySet =
    processorPropertySets.get(processorQName) match {
      case Some(ps) => ps
      case None =>
        val ps =
          (ps1.processorPropertySet(processorQName), ps2.processorPropertySet(processorQName)) match {
            case (ps1, ps2) if ps1.isEmpty => ps2
            case (ps1, ps2) if ps2.isEmpty => ps1
            case (ps1, ps2)                => new CombinedPropertySet(ps1, ps2)
          }
        processorPropertySets =
          processorPropertySets + (processorQName -> ps)
        ps
    }
}

object CombinedPropertyStore {
  // Combine multiple `PropertyStore`s into one, giving precedence to later ones in the list
  def combine(propertyStoreOptsNel: NonEmptyList[Option[PropertyStore]]): Option[PropertyStore] =
    propertyStoreOptsNel.reduceLeft[Option[PropertyStore]] {
      case (ps1Opt, ps2Opt) =>
        (ps1Opt, ps2Opt) match {
          case (Some(ps1),    Some(ps2))    => Some(new CombinedPropertyStore(ps1, ps2))
          case (s1 @ Some(_), None)         => s1
          case (None,         s2 @ Some(_)) => s2
          case (None,         None)         => None
        }
    }
}