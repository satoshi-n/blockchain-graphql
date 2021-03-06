import { ObjectType, ClassType, Field, Int } from "type-graphql";

export function PaginatedResponse<TItem>(TItemClass: ClassType<TItem>) {
    // `isAbstract` decorator option is mandatory to prevent registering in schema
    @ObjectType({ isAbstract: true })
    abstract class PaginatedResponseClass {
      // here we use the runtime argument
      @Field(type => [TItemClass])
      // and here the generic type
      items: TItem[];
  
      //@Field({complexity: 0}) //Specifying complexity here does not work so we force to implement this field in the subclass where complexity works
      abstract hasMore: boolean;
    }
    return PaginatedResponseClass;
}